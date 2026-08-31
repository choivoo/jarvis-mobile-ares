"""Production LiveKit/Gemini realtime agent for JARVIS Mobile ARES."""
from __future__ import annotations

from dotenv import load_dotenv
from livekit import agents
from livekit.agents import Agent, AgentServer, AgentSession, cli
from livekit.plugins import google

from prompts import JARVIS_INSTRUCTIONS

load_dotenv(".env.local")

AGENT_NAME = "jarvis-mobile"
VOICE_NAME = "Charon"


def health() -> dict[str, str]:
    return {
        "service": "jarvis-agent",
        "status": "ready",
        "language": "en-GB",
        "provider": "gemini-live",
    }


class JarvisAgent(Agent):
    def __init__(self) -> None:
        super().__init__(instructions=JARVIS_INSTRUCTIONS)


server = AgentServer()


@server.rtc_session(agent_name=AGENT_NAME)
async def jarvis_session(ctx: agents.JobContext) -> None:
    session = AgentSession(
        llm=google.realtime.RealtimeModel(
            voice=VOICE_NAME,
            temperature=0.6,
            instructions=JARVIS_INSTRUCTIONS,
        ),
    )
    await session.start(room=ctx.room, agent=JarvisAgent())
    await session.generate_reply(
        instructions="Greet the user briefly in calm British English. Do not use Korean speech.",
    )


if __name__ == "__main__":
    cli.run_app(server)
