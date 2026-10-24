"""Disposable process; parent kills it on timeout/cancel, then deletes all media."""
import json
import sys
import os
import threading
from pathlib import Path
from backend.config import load_environment


def main():
    load_environment()
    timeout = min(600, max(30, int(os.getenv('COOKING_AI_JOB_TIMEOUT', '300'))))
    watchdog = threading.Timer(timeout + 15, lambda: os._exit(124))
    watchdog.daemon = True
    watchdog.start()
    folder = Path(sys.argv[1]).resolve()
    try:
        from cooking_ai.inference.pipeline import analyze
        request = json.loads((folder / 'request.json').read_text())
        result = analyze(request['path'], request['kind'], request['provider'], folder / 'decoded',
                         progress=lambda value: (folder / 'progress').write_text(str(value)))
        response = {'ok': True, 'result': result}
    except Exception as failure:
        # Never serialize exceptions from providers, subprocesses, model loaders or paths.
        category = 'invalid-media-or-output' if isinstance(failure, ValueError) else 'provider-or-tool-unavailable'
        response = {'ok': False, 'error': category + ': verify media, model, ASR/OCR tools and retry'}
    (folder / 'result.json').write_text(json.dumps(response, ensure_ascii=False), encoding='utf-8')
    watchdog.cancel()


if __name__ == '__main__':
    main()
