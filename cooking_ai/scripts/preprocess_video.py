"""Export bounded frames/timeline for HUMAN annotation; never auto-label training facts."""
import argparse
import json
import os
from pathlib import Path
from cooking_ai.data.manifest import digest
from cooking_ai.preprocessing.video import VideoDecoder, KeyframeSampler, SpeechToText, OCR, TimelineAligner


def main():
    p = argparse.ArgumentParser()
    p.add_argument('video')
    p.add_argument('--output', required=True)
    a = p.parse_args()
    folder = Path(a.output)
    result = VideoDecoder().decode(a.video, folder)
    frames = KeyframeSampler().sample(result['frames'])
    speech, warnings = SpeechToText().transcribe(result['audio'], os.getenv('COOKING_AI_ASR_MODEL'))
    text, extra = OCR().read(frames)
    metadata = {'source_id': 'video-' + digest(a.video), 'duration': result['duration'],
        'frames': [{'media': str(f['path'].relative_to(folder)), 'timestamp': f['timestamp'],
                    'sha256': digest(f['path'])} for f in frames],
        'timeline': TimelineAligner().align(speech, text, frames), 'warnings': warnings + extra,
        'requires_human_annotation': True, 'permission_to_train': False}
    (folder / 'timeline.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Frames/timeline written; review ASR/OCR and annotate rights before import')


if __name__ == '__main__':
    main()
