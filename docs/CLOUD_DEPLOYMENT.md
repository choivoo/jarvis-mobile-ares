# JARVIS Mobile ARES cloud deployment

The Android app contains only a public HTTPS token endpoint URL. LiveKit API keys,
LiveKit API secret, and Gemini credentials live exclusively in the deployed services.

## Services

| Service | Container | Required server-side variables |
| --- | --- | --- |
| Token endpoint | `backend/Dockerfile` | `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `JARVIS_ALLOWED_INSTALLATIONS` |
| JARVIS Agent | `agent/Dockerfile` | `LIVEKIT_URL`, `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET`, `GOOGLE_API_KEY` |

## Endpoint contract

`GET /v1/livekit/token` requires `X-Jarvis-Installation`. It returns a 10-minute,
room-scoped participant token and dispatches only the `jarvis-mobile` agent. Unknown
installations return 403; production never permits an open token endpoint.

## Android build input

The release build receives only this public value:

`-PjarvisTokenEndpoint=https://<token-service>/v1/livekit/token`

No credential, signing password, API key, or LiveKit secret may be passed as a Gradle
property or embedded in the APK.
