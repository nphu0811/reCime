"""Bounded request bodies and local rate limits. Reverse proxy must also enforce limits."""
import json
import os
import threading
import time
from collections import OrderedDict, deque


class RequestGuard:
    def __init__(self, app):
        self.app = app
        self.hits = OrderedDict()
        self.lock = threading.Lock()

    async def __call__(self, scope, receive, send):
        if scope['type'] != 'http':
            return await self.app(scope, receive, send)
        path = scope.get('path', '')
        headers = dict(scope.get('headers', []))

        async def error(code, detail):
            body = json.dumps({'detail': detail}).encode()
            await send({'type': 'http.response.start', 'status': code,
                        'headers': [(b'content-type', b'application/json'), (b'cache-control', b'no-store')]})
            await send({'type': 'http.response.body', 'body': body})

        # Development may expose local providers without a session. Production may not.
        if os.getenv('APP_ENV', 'development') == 'production' and path.startswith(('/assistant/', '/nutrition/', '/analyze/', '/import/', '/v1/import/')):
            from backend.cloud import identity
            from fastapi import HTTPException
            try:
                identity(headers.get(b'authorization', b'').decode())
            except HTTPException:
                return await error(401, 'Login required for remote providers')
        if scope.get('method') not in ('GET', 'HEAD', 'OPTIONS'):
            client = scope.get('client') or ('unknown', 0)
            group = 'auth' if path.startswith('/v1/auth/') else 'write'
            key = (client[0], group)
            now = time.monotonic()
            with self.lock:
                hits = self.hits.setdefault(key, deque())
                while hits and now - hits[0] > 60:
                    hits.popleft()
                blocked = len(hits) >= (15 if group == 'auth' else 120)
                if not blocked:
                    hits.append(now)
                self.hits.move_to_end(key)
                while len(self.hits) > 10000:
                    self.hits.popitem(last=False)
            if blocked:
                return await error(429, 'Rate limit reached; retry after 60 seconds')
        maximum = 12 * 1024 * 1024
        try:
            if int(headers.get(b'content-length', b'0')) > maximum:
                return await error(413, 'Request maximum 12MB')
        except ValueError:
            return await error(400, 'Invalid Content-Length')
        body = bytearray()
        while True:
            message = await receive()
            if message['type'] == 'http.disconnect':
                return
            body.extend(message.get('body', b''))
            if len(body) > maximum:
                return await error(413, 'Request maximum 12MB')
            if not message.get('more_body', False):
                break
        delivered = False

        async def replay():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {'type': 'http.request', 'body': bytes(body), 'more_body': False}
            return await receive()

        async def private_send(message):
            if message['type'] == 'http.response.start':
                message['headers'] = list(message.get('headers', [])) + [(b'cache-control', b'no-store'), (b'x-content-type-options', b'nosniff')]
            await send(message)
        await self.app(scope, replay, private_send)
