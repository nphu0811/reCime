"""Bounded local-file decoding. No social downloader or remote FFmpeg inputs."""
import json
import math
import shutil
import subprocess
import time
from pathlib import Path
from PIL import Image, ImageChops, ImageStat


def command(args, timeout=35):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            timeout=timeout, check=False)
    if result.returncode:
        raise ValueError('Media decoder rejected input; use a valid local MP4/MOV')
    return result.stdout


class VideoDecoder:
    def decode(self, path, work, max_duration=180, cancelled=lambda: False):
        path, work = Path(path).resolve(), Path(work).resolve()
        if not path.is_file() or path.suffix.lower() not in ('.mp4', '.mov'):
            raise ValueError('Use a local MP4/MOV file')
        if path.stat().st_size > 10 * 1024 * 1024:
            raise ValueError('Video maximum 10MB')
        if not shutil.which('ffmpeg') or not shutil.which('ffprobe'):
            raise RuntimeError('Install FFmpeg and ffprobe')
        deadline = time.monotonic() + 150
        info = json.loads(command(['ffprobe', '-v', 'error', '-protocol_whitelist', 'file',
            '-show_format', '-show_streams', '-of', 'json', str(path)]))
        streams = info.get('streams', [])
        video = next((s for s in streams if s.get('codec_type') == 'video'), None)
        duration = float(info.get('format', {}).get('duration', 0))
        if not video or not math.isfinite(duration) or not 0 < duration <= max_duration:
            raise ValueError(f'Video must have a video stream and duration <= {max_duration}s')
        if video.get('width', 0) * video.get('height', 0) > 8_500_000:
            raise ValueError('Video resolution exceeds 8.5 megapixels')
        work.mkdir(parents=True, exist_ok=True)
        common = ['ffmpeg', '-nostdin', '-v', 'error', '-threads', '1', '-protocol_whitelist', 'file']
        frames = []
        for n in range(12):
            if cancelled() or time.monotonic() > deadline:
                raise TimeoutError('Analysis cancelled or decoding timed out')
            stamp = n * duration / 12
            target = work / f'frame-{n:02}.jpg'
            command(common + ['-ss', str(stamp), '-i', str(path), '-map', '0:v:0', '-frames:v', '1',
                    '-vf', 'scale=512:512:force_original_aspect_ratio=decrease,format=yuvj420p',
                    '-threads', '1', '-y', str(target)])
            if target.is_file():
                frames.append({'path': target, 'timestamp': stamp})
        audio = None
        if any(s.get('codec_type') == 'audio' for s in streams):
            audio = work / 'audio.wav'
            command(common + ['-i', str(path), '-map', '0:a:0', '-t', str(max_duration), '-vn',
                              '-ac', '1', '-ar', '16000', '-y', str(audio)])
        if not frames:
            raise ValueError('No decodable video frames')
        return {'duration': duration, 'frames': frames, 'audio': audio}


class KeyframeSampler:
    def sample(self, frames, maximum=6):
        # Preserve first/last; rank scene changes over a bounded temporal sample.
        scores, previous = [], None
        for index, frame in enumerate(frames):
            with Image.open(frame['path']) as image:
                small = image.convert('L').resize((32, 32))
            score = 256 if previous is None else ImageStat.Stat(ImageChops.difference(previous, small)).mean[0]
            scores.append((score, index))
            previous = small
        chosen = {0, len(frames) - 1}
        for _, index in sorted(scores, reverse=True):
            if len(chosen) >= maximum:
                break
            chosen.add(index)
        return [frames[n] for n in sorted(chosen)]


class SpeechToText:
    def transcribe(self, audio, model_path=None):
        if audio is None:
            return [], ['Video has no audio']
        if not model_path or not Path(model_path).is_dir():
            return [], ['ASR unavailable: install a local faster-whisper model; no speech was analyzed']
        from faster_whisper import WhisperModel
        model = WhisperModel(model_path, device='cpu', compute_type='int8', local_files_only=True, cpu_threads=2)
        segments, _ = model.transcribe(str(audio), language='vi', beam_size=3, vad_filter=True)
        return [{'source': 'audio', 'text': s.text.strip(), 'start': s.start, 'end': s.end}
                for s in segments if s.text.strip()], []


class OCR:
    def read(self, frames):
        try:
            import pytesseract
            languages = pytesseract.get_languages(config='')
            if not {'vie', 'eng'}.issubset(languages):
                return [], ['Vietnamese/English OCR packs unavailable; no OCR was analyzed']
        except (ImportError, OSError):
            return [], ['Tesseract unavailable; no OCR was analyzed']
        results = []
        for frame in frames:
            with Image.open(frame['path']) as image:
                text = pytesseract.image_to_string(image, lang='vie+eng', timeout=10).strip()[:4000]
            if text:
                results.append({'source': 'ocr', 'text': text, 'start': frame['timestamp'], 'end': frame['timestamp']})
        return results, []


class TimelineAligner:
    def align(self, audio, ocr, frames):
        return sorted(audio + ocr + [{'source': 'visual', 'text': f'Frame {n}',
            'start': f['timestamp'], 'end': f['timestamp']} for n, f in enumerate(frames)],
            key=lambda value: value['start'])
