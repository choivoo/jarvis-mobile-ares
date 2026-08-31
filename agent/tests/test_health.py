from main import AGENT_NAME, VOICE_NAME, health
from prompts import JARVIS_INSTRUCTIONS


def test_health_contract():
    assert health() == {
        "service": "jarvis-agent",
        "status": "ready",
        "language": "en-GB",
        "provider": "gemini-live",
    }


def test_agent_identity_is_stable():
    assert AGENT_NAME == "jarvis-mobile"
    assert VOICE_NAME


def test_language_and_safety_contract():
    prompt = JARVIS_INSTRUCTIONS.lower()
    assert "british-english" in prompt
    assert "korean subtitle" in prompt
    assert "explicit confirmation" in prompt
    assert "credentials" in prompt
