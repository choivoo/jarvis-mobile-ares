# JARVIS Mobile architecture

## Language contract

`Korean speech -> Korean STT -> KO/EN translation -> English agent -> tool result -> English voice + Korean subtitle`

The four text values are kept separately in volatile session state. Persistent diagnostic logging is off by default.

## Security boundary

The Android app receives a short-lived LiveKit session token from the backend. Gemini, LiveKit API secrets, signing keys, and provider credentials never ship in the APK. Device actions execute on Android and return typed results to the agent.

## Delivery

Every push runs unit tests, Android lint, and a debug APK build. Version tags attach the APK to GitHub Releases. Public V1.0 releases replace the temporary V0.1 debug signature with a stable encrypted signing key stored in GitHub Actions secrets.
