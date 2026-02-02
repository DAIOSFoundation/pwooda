"""
LiveKit Voice Agent
- Receives text messages from client via Data Channel
- Sends requests to existing SSE server
- Streams TTS audio back to client
"""

import asyncio
import logging
import os

from livekit import agents, rtc
from livekit.agents import AutoSubscribe, JobContext, JobProcess, WorkerOptions, cli

from agent import VoiceAgent

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


async def entrypoint(ctx: JobContext):
    """
    Entry point for the voice agent.
    Called when a room is created or when the agent joins.
    """
    logger.info(f"Agent joining room: {ctx.room.name}")

    # Wait for participant to connect
    await ctx.connect(auto_subscribe=AutoSubscribe.AUDIO_ONLY)

    # Wait for a participant (client) to join
    participant = await ctx.wait_for_participant()
    logger.info(f"Participant connected: {participant.identity}")

    # Create and start the voice agent
    agent = VoiceAgent(ctx)
    await agent.start(participant)


def prewarm(proc: JobProcess):
    """
    Prewarm function called when the worker starts.
    Can be used to load models or initialize resources.
    """
    logger.info("Prewarming voice agent worker...")
    # No heavy resources to load since we use client-side STT
    # and external SSE server for LLM/TTS


if __name__ == "__main__":
    cli.run_app(
        WorkerOptions(
            entrypoint_fnc=entrypoint,
            prewarm_fnc=prewarm,
        )
    )
