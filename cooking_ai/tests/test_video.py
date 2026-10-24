import shutil
import tempfile
import unittest
from pathlib import Path
from cooking_ai.preprocessing.video import VideoDecoder, KeyframeSampler, SpeechToText, TimelineAligner, command
from cooking_ai.inference.pipeline import RecipeSchemaValidator
from cooking_ai.schema import Recipe


@unittest.skipUnless(shutil.which('ffmpeg') and shutil.which('ffprobe'), 'FFmpeg not installed')
class VideoTests(unittest.TestCase):
    def test_real_mp4_silent_and_audio_decode(self):
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            for audio in (False, True):
                target = folder / ('audio.mp4' if audio else 'silent.mp4')
                args = ['ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i', 'testsrc2=size=160x120:rate=5']
                if audio:
                    args += ['-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=16000']
                command(args + ['-t', '2', '-c:v', 'mpeg4', '-y', str(target)])
                decoded = VideoDecoder().decode(target, folder / target.stem)
                self.assertAlmostEqual(decoded['duration'], 2, delta=0.2)
                selected = KeyframeSampler().sample(decoded['frames'])
                self.assertGreater(len(selected), 0)
                self.assertLessEqual(len(selected), 6)
                self.assertEqual(decoded['audio'] is not None, audio)
                timeline = TimelineAligner().align([], [], selected)
                self.assertEqual(sorted(t['start'] for t in timeline), [t['start'] for t in timeline])
                if audio:
                    self.assertGreater(decoded['audio'].stat().st_size, 1000)

    def test_duration_and_invalid_input(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'invalid.mp4'
            path.write_bytes(b'not a video')
            with self.assertRaises(ValueError):
                VideoDecoder().decode(path, Path(directory) / 'decoded')
            command(['ffmpeg', '-nostdin', '-v', 'error', '-f', 'lavfi', '-i', 'color=size=32x32',
                     '-t', '2', '-c:v', 'mpeg4', '-y', str(path)])
            with self.assertRaises(ValueError):
                VideoDecoder().decode(path, Path(directory) / 'decoded', max_duration=1)


class EvidenceTests(unittest.TestCase):
    def test_timestamps_and_quote_grounding(self):
        value = {'is_cooking': True, 'title': 'Cơm', 'language': 'vi', 'ingredients': [],
            'steps': [{'text': 'Vo gạo', 'evidence': {'source': 'audio', 'text': 'Vo gạo', 'start': 1, 'end': 2}}],
            'uncertainty': [], 'suggested_recipe': None}
        recipe = Recipe.model_validate(value)
        validator = RecipeSchemaValidator()
        timeline = [{'source': 'audio', 'text': 'Vo gạo', 'start': 1, 'end': 2}]
        self.assertEqual(validator.validate(recipe, timeline, 3).title, 'Cơm')
        for transcript, duration in [([], 3), (timeline, 1), (timeline, None)]:
            with self.assertRaises(ValueError):
                validator.validate(recipe, transcript, duration)
        self.assertTrue(SpeechToText().transcribe(None)[1])
