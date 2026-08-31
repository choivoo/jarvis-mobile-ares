"""Production LiveKit/Gemini realtime agent for JARVIS Mobile ARES."""
from __future__ import annotations

import asyncio
import json

from dotenv import load_dotenv
from livekit import agents, rtc
from livekit.agents import Agent, AgentServer, AgentSession, cli
from livekit.plugins import google

from prompts import JARVIS_INSTRUCTIONS

load_dotenv(".env.local")

AGENT_NAME = "jarvis-mobile"
VOICE_NAME = "Charon"
COMMAND_TOPIC = "jarvis.command.en.v1"
RESPONSE_TOPIC = "jarvis.response.en.v1"


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

    @ctx.room.on("data_received")
    def receive_command(packet: rtc.DataPacket) -> None:
        """Accept only the Android client's already-translated English command channel."""
        if packet.topic != COMMAND_TOPIC or packet.participant is None:
            return
        try:
            command = json.loads(packet.data.decode("utf-8")).get("text", "").strip()
        except (UnicodeDecodeError, json.JSONDecodeError):
            return
        if command:
            asyncio.create_task(reply_to_command(command))

    async def reply_to_command(command: str) -> None:
        await session.generate_reply(user_input=command)

    @session.on("conversation_item_added")
    def publish_response(event) -> None:
        """Return agent English text for Android's EN -> KO subtitle branch."""
        item = event.item
        text = getattr(item, "raw_text_content", "")
        if getattr(item, "role", "") != "assistant" or not text:
            return
        payload = json.dumps({"english": text, "schema": 1}).encode("utf-8")
        asyncio.create_task(ctx.room.local_participant.publish_data(payload, topic=RESPONSE_TOPIC))


if __name__ == "__main__":
    cli.run_app(server)
