from main import AGENT_NAME, COMMAND_TOPIC, RESPONSE_TOPIC, VOICE_NAME, health
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


def test_android_data_channel_contract_is_stable():
    assert COMMAND_TOPIC == "jarvis.command.en.v1"
    assert RESPONSE_TOPIC == "jarvis.response.en.v1"


def test_language_and_safety_contract():
    prompt = JARVIS_INSTRUCTIONS.lower()
    assert "british-english" in prompt
    assert "korean subtitle" in prompt
    assert "explicit confirmation" in prompt
    assert "credentials" in prompt
