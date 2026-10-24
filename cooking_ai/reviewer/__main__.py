import argparse
import json
import webbrowser
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description='Cooking AI Dataset Reviewer — chỉ local, không AI/provider ngoài')
    parser.add_argument('command', nargs='?', choices=('serve', 'validate'), default='serve')
    parser.add_argument('--manifest', default='cooking_ai/data/private/wiki/manifest.jsonl')
    parser.add_argument('--state-dir', default='cooking_ai/data/private/reviewer/wiki')
    parser.add_argument('--output-dir', default='cooking_ai/data/private/real')
    parser.add_argument('--port', type=int, default=8770)
    parser.add_argument('--open-browser', action='store_true')
    args = parser.parse_args()
    manifest = Path(args.manifest)
    if not manifest.exists():
        if manifest.resolve() != Path('cooking_ai/data/private/wiki/manifest.jsonl').resolve():
            parser.error('Manifest không tồn tại; chỉ tự chuẩn bị dataset Wiki mặc định khi thiếu')
        from cooking_ai.scripts.from_catalog import export
        export(Path('app/src/main/assets/catalog'), manifest.parent)
    if args.command == 'validate':
        from cooking_ai.reviewer.store import ReviewerStore
        report = ReviewerStore(manifest, args.state_dir, args.output_dir).export(publish=False)
        print(json.dumps(report, ensure_ascii=True, indent=2))
        raise SystemExit(1 if report['failed'] else 0)
    from cooking_ai.reviewer.server import create_app
    import uvicorn
    app = create_app(manifest, args.state_dir, args.output_dir)
    print(json.dumps({**app.state.store.session(), 'url': f'http://127.0.0.1:{args.port}'}, ensure_ascii=True))
    if args.open_browser:
        import threading
        threading.Timer(1, lambda: webbrowser.open(f'http://127.0.0.1:{args.port}')).start()
    uvicorn.run(app, host='127.0.0.1', port=args.port, workers=1, access_log=False)


if __name__ == '__main__':
    main()
