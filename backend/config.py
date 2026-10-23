"""Load only backend/.env, without overriding process environment or exposing values."""
import os
from pathlib import Path


def load_environment():
    path = Path(__file__).with_name('.env')
    if path.exists():
        for raw in path.read_text(encoding='utf-8-sig').splitlines():
            line = raw.strip()
            if line and not line.startswith('#') and '=' in line:
                name, value = line.split('=', 1)
                if name.strip().replace('_', '').isalnum():
                    os.environ.setdefault(name.strip(), value.strip().strip('"\''))


def database_path():
    value = os.getenv('DATABASE_URL', 'sqlite:///backend/data/recime.db')
    if not value.startswith('sqlite:///'):
        raise RuntimeError('DATABASE_URL currently supports sqlite:/// paths only')
    return Path(value[len('sqlite:///'):]).resolve()
