"""
Twilio Bridge Service
- Handles Twilio voice webhooks and Media Streams
- Bridges phone calls to AI agent via SSE server (LLM + TTS)
- Supports both inbound and outbound calls
"""

import asyncio
import base64
import json
import logging
import os
from queue import Queue
from threading import Thread
from typing import Optional

import requests
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.responses import Response
from fastapi.middleware.cors import CORSMiddleware
from twilio.rest import Client as TwilioClient
from twilio.twiml.voice_response import VoiceResponse, Connect
import uvicorn

from audio_utils import pcm_24k_to_mulaw_8k, chunk_audio
from stt_handler import STTHandler

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)

# Configuration
TWILIO_ACCOUNT_SID = os.environ.get("TWILIO_ACCOUNT_SID", "")
TWILIO_AUTH_TOKEN = os.environ.get("TWILIO_AUTH_TOKEN", "")
TWILIO_PHONE_NUMBER = os.environ.get("TWILIO_PHONE_NUMBER", "+18312733281")
TWILIO_WEBHOOK_BASE_URL = os.environ.get("TWILIO_WEBHOOK_BASE_URL", "")

SSE_SERVER_URL = os.environ.get(
    "SSE_SERVER_URL", "http://210.109.53.87:8083/completion-with-tts"
)
SSE_AUTH_TOKEN = os.environ.get("SSE_AUTH_TOKEN", "")

# FastAPI app
app = FastAPI(title="Twilio Bridge Service")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Twilio client
twilio_client: Optional[TwilioClient] = None


@app.on_event("startup")
async def startup():
    global twilio_client
    if TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN:
        twilio_client = TwilioClient(TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN)
        logger.info("Twilio client initialized")
    else:
        logger.warning("Twilio credentials not set")


@app.get("/health")
async def health():
    return {"status": "ok", "service": "twilio-bridge"}


# ──────────────────────────────────────────────
# Twilio Voice Webhook (TwiML)
# ──────────────────────────────────────────────

@app.post("/twilio/voice")
async def twilio_voice_webhook(request: Request):
    """
    Twilio webhook for incoming/outgoing calls.
    Returns TwiML that connects the call to a Media Stream WebSocket.
    """
    logger.info("Twilio voice webhook called")

    response = VoiceResponse()
    response.say("안녕하세요, AI 에이전트에 연결합니다. 잠시만 기다려주세요.", language="ko-KR")

    # Determine WebSocket URL
    ws_base = TWILIO_WEBHOOK_BASE_URL.replace("https://", "wss://").replace(
        "http://", "ws://"
    )
    stream_url = f"{ws_base}/twilio/stream"

    connect = Connect()
    connect.stream(url=stream_url)
    response.append(connect)

    logger.info(f"TwiML stream URL: {stream_url}")

    return Response(content=str(response), media_type="application/xml")


# ──────────────────────────────────────────────
# Outbound Call API
# ──────────────────────────────────────────────

@app.post("/api/v1/call/initiate")
async def initiate_call(request: Request):
    """
    Initiate an outbound call via Twilio.
    The call will connect the recipient to the AI agent.
    """
    if not twilio_client:
        return {"error": "Twilio client not initialized"}, 500

    body = await request.json()
    to_number = body.get("to", "+8201062904539")

    # TwiML webhook URL for the outbound call
    twiml_url = f"{TWILIO_WEBHOOK_BASE_URL}/twilio/voice"

    try:
        call = twilio_client.calls.create(
            url=twiml_url,
            to=to_number,
            from_=TWILIO_PHONE_NUMBER,
        )

        logger.info(f"Outbound call initiated: SID={call.sid}, to={to_number}")
        return {
            "success": True,
            "call_sid": call.sid,
            "to": to_number,
            "from": TWILIO_PHONE_NUMBER,
        }

    except Exception as e:
        logger.error(f"Failed to initiate call: {e}")
        return {"error": str(e)}, 500


# ──────────────────────────────────────────────
# SSE Client (reused from voice-agent)
# ──────────────────────────────────────────────

def fetch_sse_sync(payload: dict, event_queue: Queue):
    """Synchronous SSE fetch in a separate thread."""
    headers = {
        "Authorization": f"Bearer {SSE_AUTH_TOKEN}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    }

    try:
        logger.info(f"[SSE Thread] Starting request to {SSE_SERVER_URL}")

        with requests.post(
            SSE_SERVER_URL, json=payload, headers=headers, stream=True, timeout=300
        ) as response:
            if response.status_code != 200:
                logger.error(f"[SSE Thread] Server error: {response.status_code}")
                event_queue.put({"type": "error", "message": f"Server error: {response.status_code}"})
                event_queue.put(None)
                return

            buffer = ""
            for chunk in response.iter_content(chunk_size=8192, decode_unicode=True):
                if chunk:
                    buffer += chunk
                    while "\n" in buffer:
                        line, buffer = buffer.split("\n", 1)
                        line = line.strip()
                        if not line:
                            continue
                        if line.startswith("data:"):
                            data_str = line[5:].strip()
                            if data_str == "[DONE]":
                                event_queue.put({"type": "done"})
                                event_queue.put(None)
                                return
                            try:
                                event = json.loads(data_str)
                                event_queue.put(event)
                            except json.JSONDecodeError:
                                pass

            event_queue.put(None)

    except Exception as e:
        logger.error(f"[SSE Thread] Error: {e}")
        event_queue.put({"type": "error", "message": str(e)})
        event_queue.put(None)


# ──────────────────────────────────────────────
# Twilio Media Streams WebSocket
# ──────────────────────────────────────────────

@app.websocket("/twilio/stream")
async def twilio_stream(websocket: WebSocket):
    """
    Handle Twilio Media Streams WebSocket connection.

    Flow:
    1. Receive audio from Twilio (mulaw 8kHz)
    2. Run VAD + STT to detect and transcribe speech
    3. Send transcribed text to SSE server (LLM + TTS)
    4. Stream TTS audio back to Twilio (converted to mulaw 8kHz)
    """
    await websocket.accept()
    logger.info("Twilio Media Stream WebSocket connected")

    stream_sid: Optional[str] = None
    stt_handler: Optional[STTHandler] = None
    processed_sentence_ids: set = set()
    is_processing = False

    async def send_audio_to_twilio(mulaw_data: bytes):
        """Send mulaw audio back to Twilio via WebSocket."""
        if not stream_sid:
            return

        # Split into 20ms chunks (640 bytes of mulaw at 8kHz)
        chunks = chunk_audio(mulaw_data, chunk_size=640)

        for chunk in chunks:
            payload = base64.b64encode(chunk).decode("utf-8")
            message = {
                "event": "media",
                "streamSid": stream_sid,
                "media": {"payload": payload},
            }
            try:
                await websocket.send_json(message)
            except Exception as e:
                logger.error(f"Error sending audio to Twilio: {e}")
                return

    async def process_speech(text: str):
        """Process recognized speech through SSE server and stream TTS back."""
        nonlocal is_processing, processed_sentence_ids

        if is_processing:
            logger.info("Already processing, skipping")
            return

        is_processing = True
        processed_sentence_ids.clear()
        logger.info(f"Processing speech: {text}")

        # Build payload (same format as voice-agent)
        formatted_prompt = (
            f"<|start_header_id|>user<|end_header_id|>\n\n"
            f"{text}<|eot_id|>"
            f"<|start_header_id|>assistant<|end_header_id|>\n\n"
        )

        payload = {
            "prompt": formatted_prompt,
            "stream": True,
            "tts": {"enabled": True, "voiceName": "ko-KR-Wavenet-A"},
        }

        # Call SSE server in a thread
        event_queue: Queue = Queue()
        thread = Thread(target=fetch_sse_sync, args=(payload, event_queue))
        thread.daemon = True
        thread.start()

        try:
            while True:
                try:
                    event = await asyncio.to_thread(event_queue.get, timeout=1.0)
                except Exception as e:
                    if "Empty" in str(type(e).__name__):
                        continue
                    break

                if event is None:
                    break

                event_type = event.get("type")

                if event_type == "audio":
                    audio_b64 = event.get("audio", "")
                    sentence_id = event.get("sentenceId", 0)

                    if sentence_id in processed_sentence_ids:
                        continue
                    processed_sentence_ids.add(sentence_id)

                    if audio_b64:
                        # Decode base64 PCM (24kHz) -> convert to mulaw (8kHz)
                        pcm_24k = base64.b64decode(audio_b64)
                        mulaw_8k = pcm_24k_to_mulaw_8k(pcm_24k)

                        # Send to Twilio
                        await send_audio_to_twilio(mulaw_8k)
                        logger.info(
                            f"Sent TTS audio: sentence={sentence_id}, "
                            f"pcm_size={len(pcm_24k)}, mulaw_size={len(mulaw_8k)}"
                        )

                elif event_type == "text":
                    content = event.get("content", "")
                    logger.debug(f"LLM text: {content[:50]}")

                elif event_type == "done":
                    logger.info("SSE response completed")
                    break

                elif event_type == "error":
                    logger.error(f"SSE error: {event.get('message')}")
                    break

        except Exception as e:
            logger.error(f"Error processing SSE response: {e}")
        finally:
            is_processing = False
            thread.join(timeout=1.0)

    try:
        # Initialize STT handler
        stt_handler = STTHandler(language="ko", model_size="base")

        while True:
            data = await websocket.receive_text()
            message = json.loads(data)
            event = message.get("event")

            if event == "connected":
                logger.info("Twilio stream connected")

            elif event == "start":
                stream_sid = message["start"]["streamSid"]
                call_sid = message["start"].get("callSid", "unknown")
                logger.info(f"Stream started: streamSid={stream_sid}, callSid={call_sid}")

            elif event == "media":
                # Decode mulaw audio from Twilio
                payload = message["media"]["payload"]
                mulaw_bytes = base64.b64decode(payload)

                # Process through STT (VAD + Whisper)
                text = stt_handler.process_audio(mulaw_bytes)

                if text:
                    # Speech detected and transcribed - send to AI
                    await process_speech(text)

            elif event == "stop":
                logger.info("Twilio stream stopped")
                # Flush any remaining audio in STT buffer
                if stt_handler:
                    text = stt_handler.flush()
                    if text:
                        await process_speech(text)
                break

    except WebSocketDisconnect:
        logger.info("Twilio WebSocket disconnected")
    except Exception as e:
        logger.error(f"WebSocket error: {e}", exc_info=True)
    finally:
        logger.info("Twilio Media Stream session ended")


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8082)
