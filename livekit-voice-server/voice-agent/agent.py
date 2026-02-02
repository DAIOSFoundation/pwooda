"""
Voice Agent implementation
- Handles text messages from client
- Calls existing SSE server for LLM/TTS
- Streams audio back to client
"""

import asyncio
import base64
import json
import logging
import os
from typing import Optional

from livekit import rtc
from livekit.agents import JobContext

from sse_client import SSEClient

logger = logging.getLogger(__name__)

# Configuration
SSE_SERVER_URL = os.environ.get("SSE_SERVER_URL", "http://210.109.53.87/completion-with-tts")
SSE_AUTH_TOKEN = os.environ.get("SSE_AUTH_TOKEN", "")


class VoiceAgent:
    """
    Voice agent that handles text-based conversation with TTS streaming.

    Flow:
    1. Receive text message from client via Data Channel
    2. Send to SSE server for LLM processing
    3. Stream TTS audio back to client via AudioTrack
    """

    def __init__(self, ctx: JobContext):
        self.ctx = ctx
        self.room = ctx.room
        self.sse_client = SSEClient(SSE_SERVER_URL, SSE_AUTH_TOKEN)

        # Audio source for TTS output (24kHz mono)
        self.audio_source: Optional[rtc.AudioSource] = None
        self.audio_track: Optional[rtc.LocalAudioTrack] = None

        # State
        self.is_speaking = False
        self.is_interrupted = False
        self.current_task: Optional[asyncio.Task] = None
        self.conversation_id: Optional[str] = None
        self.processed_sentence_ids: set = set()

    async def start(self, participant: rtc.RemoteParticipant):
        """Start the voice agent for a participant."""
        logger.info(f"Starting voice agent for {participant.identity}")

        # Extract conversation_id from participant metadata
        if participant.metadata:
            try:
                metadata = json.loads(participant.metadata)
                self.conversation_id = metadata.get("conversation_id")
                logger.info(f"Conversation ID: {self.conversation_id}")
            except json.JSONDecodeError:
                pass

        # Create audio source (24kHz mono, matching SSE server output)
        self.audio_source = rtc.AudioSource(
            sample_rate=24000,
            num_channels=1
        )

        # Create and publish audio track
        self.audio_track = rtc.LocalAudioTrack.create_audio_track(
            "ai-voice",
            self.audio_source
        )

        await self.room.local_participant.publish_track(
            self.audio_track,
            rtc.TrackPublishOptions(source=rtc.TrackSource.SOURCE_MICROPHONE)
        )
        logger.info("Audio track published")

        # Store target participant for later use
        self._target_participant = participant

        # Set up event handlers (use sync wrapper for async callbacks)
        # livekit-rtc passes DataPacket object with: data, participant, kind, topic attributes
        def on_data_received_sync(packet: rtc.DataPacket):
            asyncio.create_task(self._on_data_received(packet))

        def on_participant_disconnected_sync(p: rtc.RemoteParticipant):
            asyncio.create_task(self._on_participant_disconnected(p))

        self.room.on("data_received", on_data_received_sync)
        self.room.on("participant_disconnected", on_participant_disconnected_sync)

        # Keep the agent running
        try:
            while True:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            logger.info("Agent cancelled")

    async def _on_data_received(self, packet: rtc.DataPacket):
        """Handle data received from client via Data Channel."""
        try:
            # Extract data from packet
            data = packet.data
            participant_identity = packet.participant.identity if packet.participant else None
            logger.info(f"Data received from {participant_identity}: {len(data)} bytes")

            message = json.loads(data.decode('utf-8'))
            msg_type = message.get("type")

            if msg_type == "text":
                # User text message (from client-side STT)
                text = message.get("content", "")
                if text.strip():
                    logger.info(f"Received text: {text}")

                    # Cancel any ongoing response
                    if self.current_task and not self.current_task.done():
                        self.is_interrupted = True
                        self.current_task.cancel()
                        await asyncio.sleep(0.1)

                    # Process the message
                    self.is_interrupted = False
                    self.current_task = asyncio.create_task(
                        self._process_message(text, self._target_participant)
                    )

            elif msg_type == "interrupt":
                # User wants to interrupt AI response
                logger.info("Interrupt received")
                self.is_interrupted = True
                if self.current_task and not self.current_task.done():
                    self.current_task.cancel()

            elif msg_type == "ping":
                # Keep-alive ping
                await self._send_data(self._target_participant, {"type": "pong"})

        except json.JSONDecodeError:
            logger.warning(f"Invalid JSON received: {data}")
        except Exception as e:
            logger.error(f"Error handling data: {e}", exc_info=True)

    async def _process_message(self, text: str, participant: rtc.RemoteParticipant):
        """Process a text message and stream TTS response."""
        logger.info(f"Processing message: {text}")
        self.is_speaking = True
        self.processed_sentence_ids.clear()

        try:
            # Notify client that processing started
            logger.info("Sending processing status to client")
            await self._send_data(participant, {
                "type": "status",
                "status": "processing"
            })

            # Build request payload with correct prompt format for LLM server
            # Format: <|start_header_id|>user<|end_header_id|>\n\n{text}<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n
            formatted_prompt = f"<|start_header_id|>user<|end_header_id|>\n\n{text}<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"

            payload = {
                "prompt": formatted_prompt,
                "stream": True,
                "tts": {
                    "enabled": True,
                    "voiceName": "ko-KR-Wavenet-A"
                }
            }

            # Add conversation_id if available
            if self.conversation_id:
                payload["conversation_id"] = self.conversation_id

            # Stream from SSE server
            logger.info(f"Calling SSE server: {SSE_SERVER_URL}")
            logger.info(f"Payload: prompt={formatted_prompt[:50]}..., tts={payload.get('tts')}")
            accumulated_text = ""

            event_count = 0
            async for event in self.sse_client.stream(payload):
                event_count += 1
                if event_count <= 5:
                    logger.info(f"SSE event #{event_count}: type={event.get('type')}")
                if self.is_interrupted:
                    logger.info("Response interrupted, stopping")
                    break

                event_type = event.get("type")

                if event_type == "text":
                    # Text chunk from LLM
                    content = event.get("content", "")
                    accumulated_text += content

                    # Send text to client for display
                    await self._send_data(participant, {
                        "type": "text",
                        "content": content
                    })

                elif event_type == "audio":
                    # TTS audio chunk
                    audio_b64 = event.get("audio", "")
                    sentence_id = event.get("sentenceId", 0)

                    # Avoid duplicate audio chunks
                    if sentence_id in self.processed_sentence_ids:
                        continue
                    self.processed_sentence_ids.add(sentence_id)

                    if audio_b64:
                        await self._play_audio(audio_b64)

                elif event_type == "done":
                    logger.info("SSE response completed")
                    break

            # Notify client that response is complete
            await self._send_data(participant, {
                "type": "status",
                "status": "done"
            })

        except asyncio.CancelledError:
            logger.info("Message processing cancelled")
        except Exception as e:
            logger.error(f"Error processing message: {e}")
            await self._send_data(participant, {
                "type": "error",
                "message": str(e)
            })
        finally:
            self.is_speaking = False

    async def _play_audio(self, audio_b64: str):
        """Play base64-encoded PCM audio through the audio source."""
        try:
            # Decode base64 to PCM bytes
            pcm_bytes = base64.b64decode(audio_b64)

            # Calculate frame parameters (24kHz, 16-bit mono)
            # Each sample is 2 bytes (16-bit)
            samples_per_channel = len(pcm_bytes) // 2

            # Create audio frame
            frame = rtc.AudioFrame(
                data=pcm_bytes,
                sample_rate=24000,
                num_channels=1,
                samples_per_channel=samples_per_channel
            )

            # Send to audio source
            await self.audio_source.capture_frame(frame)

        except Exception as e:
            logger.error(f"Error playing audio: {e}")

    async def _send_data(self, participant: rtc.RemoteParticipant, data: dict):
        """Send data to a specific participant via Data Channel."""
        try:
            json_data = json.dumps(data).encode('utf-8')
            await self.room.local_participant.publish_data(
                json_data,
                reliable=True,
                destination_identities=[participant.identity]
            )
        except Exception as e:
            logger.error(f"Error sending data: {e}")

    async def _on_participant_disconnected(self, participant: rtc.RemoteParticipant):
        """Handle participant disconnection."""
        logger.info(f"Participant disconnected: {participant.identity}")

        # Cancel any ongoing task
        if self.current_task and not self.current_task.done():
            self.current_task.cancel()
