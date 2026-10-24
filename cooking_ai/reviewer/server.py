import secrets
from pathlib import Path
from urllib.parse import urlsplit
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse
from cooking_ai.data.manifest import media_path
from cooking_ai.reviewer.store import Draft, ReviewerStore, StaleDraft


def create_app(manifest, state_dir, output_dir):
    app = FastAPI(title='Cooking AI Dataset Reviewer', docs_url=None, redoc_url=None, openapi_url=None)
    app.state.store = ReviewerStore(manifest, state_dir, output_dir)
    token = secrets.token_urlsafe(32)
    store = app.state.store

    @app.middleware('http')
    async def local_only(request: Request, call_next):
        host = request.headers.get('host', '')
        try:
            parsed = urlsplit('http://' + host)
            allowed = parsed.hostname in ('localhost', '127.0.0.1', '::1') and not parsed.username and not parsed.password
            origin = request.headers.get('origin')
            same_origin = not origin or origin == 'http://' + host
        except ValueError:
            allowed = same_origin = False
        if not allowed or not same_origin or request.headers.get('sec-fetch-site') == 'cross-site':
            return JSONResponse({'detail': 'Local same-origin requests only'}, status_code=403)
        if request.method not in ('GET', 'HEAD'):
            if not secrets.compare_digest(request.headers.get('x-reviewer-csrf', ''), token):
                return JSONResponse({'detail': 'Missing reviewer session token'}, status_code=403)
            body = bytearray()
            async for part in request.stream():
                body.extend(part)
                if len(body) > 256 * 1024:
                    return JSONResponse({'detail': 'Draft maximum 256KB'}, status_code=413)
            request._body = bytes(body)
        response = await call_next(request)
        response.headers['Cache-Control'] = 'no-store'
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['Cross-Origin-Resource-Policy'] = 'same-origin'
        response.headers['Content-Security-Policy'] = "default-src 'self'; img-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"
        response.headers['Referrer-Policy'] = 'no-referrer'
        return response

    @app.exception_handler(StaleDraft)
    async def stale(request, failure):
        return JSONResponse({'detail': str(failure)}, status_code=409)

    @app.exception_handler(ValueError)
    async def invalid(request, failure):
        return JSONResponse({'detail': str(failure)}, status_code=422)

    @app.exception_handler(IndexError)
    async def missing(request, failure):
        return JSONResponse({'detail': 'Ảnh không tồn tại'}, status_code=404)

    @app.get('/')
    def home():
        return FileResponse(Path(__file__).with_name('index.html'), media_type='text/html')

    @app.get('/api/session')
    def session():
        return {**store.session(), 'csrf': token}

    @app.get('/api/items/{index}')
    def item(index: int):
        return store.item(index)

    @app.put('/api/items/{index}')
    def save(index: int, body: Draft):
        return store.save(index, body)

    @app.get('/media/{index}')
    def media(index: int):
        row = store.original(index)
        path = media_path(store.root, row.media)
        if path.suffix.lower() not in ('.jpg', '.jpeg', '.png', '.webp'):
            raise HTTPException(422, 'Unsupported image format')
        return FileResponse(path)

    @app.post('/api/export')
    def export():
        return store.export()

    @app.post('/api/validate')
    def validate_current():
        return store.export(publish=False)

    return app
