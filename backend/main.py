"""Secure, short-lived LiveKit token service for JARVIS Mobile ARES.

LiveKit API credentials remain only in this process environment. The Android APK receives
one room-scoped, short-lived participant JWT and never receives an API secret.
"""
from __future__ import annotations

import datetime as dt
import os
import re

from fastapi import FastAPI, Header, HTTPException
from livekit import api

AGENT_NAME = "jarvis-mobile"
INSTALLATION_PATTERN = re.compile(r"^[0-9a-f]{8}-[0-9a-f-]{27,36}$", re.IGNORECASE)


def _allowed_installations() -> set[str]:
    return {value.strip() for value in os.getenv("JARVIS_ALLOWED_INSTALLATIONS", "").split(",") if value.strip()}


def _validate_installation(installation: str | None) -> str:
    if not installation or not INSTALLATION_PATTERN.fullmatch(installation):
        raise HTTPException(status_code=401, detail="Invalid installation identity")
    allowlist = _allowed_installations()
    # Fail closed. A production endpoint must explicitly register each personal device.
    if not allowlist or installation not in allowlist:
        raise HTTPException(status_code=403, detail="Installation is not authorized")
    return installation


def create_token(installation: str) -> dict[str, str]:
    livekit_url = os.getenv("LIVEKIT_URL", "")
    if not livekit_url.startswith("wss://"):
        raise RuntimeError("LIVEKIT_URL must be a wss:// endpoint")
    room_name = f"jarvis-{installation.replace('-', '')[:20]}"
    token = (
        api.AccessToken()
        .with_identity(f"android-{installation}")
        .with_name("JARVIS Mobile")
        .with_ttl(dt.timedelta(minutes=10))
        .with_grants(
            api.VideoGrants(
                room_join=True,
                room=room_name,
                can_publish=True,
                can_subscribe=True,
                can_publish_data=True,
            ),
        )
        .with_room_config(
            api.RoomConfiguration(
                agents=[api.RoomAgentDispatch(agent_name=AGENT_NAME)],
            ),
        )
        .to_jwt()
    )
    return {"serverUrl": livekit_url, "participantToken": token}


app = FastAPI(title="JARVIS Mobile Token Service", docs_url=None, redoc_url=None)


@app.get("/health")
def health() -> dict[str, str]:
    return {"service": "jarvis-token-service", "status": "ready"}


@app.get("/v1/livekit/token")
def token(x_jarvis_installation: str | None = Header(default=None)) -> dict[str, str]:
    installation = _validate_installation(x_jarvis_installation)
    try:
        return create_token(installation)
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail="LiveKit service is not configured") from exc
