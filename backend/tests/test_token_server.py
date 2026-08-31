import sys
from pathlib import Path

import pytest

sys.path.append(str(Path(__file__).resolve().parents[1]))
from main import _validate_installation, create_token  # noqa: E402


def test_invalid_installation_is_rejected(monkeypatch):
    monkeypatch.setenv("JARVIS_ALLOWED_INSTALLATIONS", "123e4567-e89b-12d3-a456-426614174000")
    with pytest.raises(Exception):
        _validate_installation("not-an-installation")


def test_unknown_installation_is_rejected(monkeypatch):
    monkeypatch.setenv("JARVIS_ALLOWED_INSTALLATIONS", "123e4567-e89b-12d3-a456-426614174000")
    with pytest.raises(Exception):
        _validate_installation("123e4567-e89b-12d3-a456-426614174999")


def test_token_service_requires_secure_room_url(monkeypatch):
    monkeypatch.setenv("LIVEKIT_URL", "http://not-secure.example")
    with pytest.raises(RuntimeError):
        create_token("123e4567-e89b-12d3-a456-426614174000")
