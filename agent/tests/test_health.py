from main import health


def test_health_contract():
    assert health() == {"service": "jarvis-agent", "status": "ready", "language": "en-GB"}
