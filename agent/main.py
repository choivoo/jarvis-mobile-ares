"""JARVIS Mobile agent entry point and V0.1 health contract."""
from prompts import JARVIS_INSTRUCTIONS


def health() -> dict[str, str]:
    return {"service": "jarvis-agent", "status": "ready", "language": "en-GB"}


if __name__ == "__main__":
    print(health())
    print(f"Prompt loaded: {len(JARVIS_INSTRUCTIONS)} chars")
