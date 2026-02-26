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
    "SSE_SERVER_URL", "https://eu6l8rgf-8083.thundercompute.net/completion-with-tts"
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
        """Send mulaw audio back to Twilio via WebSocket with pacing."""
        if not stream_sid:
            return

        # Split into 20ms chunks (160 bytes of mulaw at 8kHz)
        CHUNK_SIZE = 160  # 8000 Hz * 1 byte * 0.02s = 160 bytes per 20ms
        chunks = chunk_audio(mulaw_data, chunk_size=CHUNK_SIZE)

        # Send chunks with pacing to match real-time playback
        # Send in batches of 10 chunks (200ms) with a small delay
        BATCH_SIZE = 10
        for i, chunk in enumerate(chunks):
            payload = base64.b64encode(chunk).decode("utf-8")
            message = {
                "event": "media",
                "streamSid": stream_sid,
                "media": {"payload": payload},
            }
            try:
                await websocket.send_json(message)
                # Pace every batch to avoid overwhelming Twilio buffer
                if (i + 1) % BATCH_SIZE == 0:
                    await asyncio.sleep(0.15)  # ~150ms per 200ms of audio
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
        system_prompt = (
            "너는 한국어로 대답하는 서울 시립 복지 센터의 친절한 상담원 소희야.\n"
            "1, 2, 3 과 같은 숫자는 대답에 사용하지 말아줘.\n"
            "최대한 3문장 이내로 간단하게 대답해줘.\n"
            "2026년에 추가된 복지 정책은 아래와 같으니 관련 질문엔 아래 내용을 참고해서 대답해.\n\n"
            "[2026 대한민국 & 서울시 복지 정책 상세]\n"
            "국민기초생활보장 (국가 공통)\n"
            "생계급여: 사인 가구 기준 월 최대 이백칠만팔천 원 지급.\n"
            "의료급여: 가상의 부양비 반영 제도 폐지 (실질 빈곤층 보호 강화).\n"
            "재산 기준 완화: 생업 필수 차량 및 다자녀 가구 승용차는 수급권 탈락 요인에서 제외.\n"
            "청년 소득공제: 대상 확대(삼십사세 이하), 공제액 상향(육십만 원).\n"
            "위기 발굴: AI 기반 단전·단수 데이터 연동 시스템 및 '그냥드림 코너' 전국 운영.\n\n"
            "생애주기별 지원 체계\n"
            "아동(영세에서 구세): 아동수당 대상: 구세 미만 (비수도권 추가 지원).\n"
            "인프라: 이십사시 야간 돌봄, 달빛어린이병원 백이십개소.\n"
            "의료비: 이른둥이 최대 이천만 원 지원.\n"
            "청년(십구세에서 삼십사세): 청년미래적금: 월 오십만 원 저축 시 육퍼센트 매칭 (중소기업 재직자 십이퍼센트 매칭).\n"
            "군 복무 크레딧: 복무 전 기간 국민연금 가입 기간 인정.\n"
            "주거: 서울시 청년 월세 지원 상시 신청제 전환.\n"
            "노인(육십오세 이상): 연금: 부부 감액 제도 단계적 완화, 노령연금 감액 기준 소득 상향(오백구만 원).\n"
            "통합지원: 이공이육년 삼월 이십칠일 시행 '의료·요양 지역 돌봄법'에 따라 자택 중심 통합 서비스 제공.\n\n"
            "서울특별시 특화 정책\n"
            "디딤돌소득(구 안심소득): 하후상박형 소득 보장 시범사업(약 천백가구) 실증 연구 단계.\n"
            "서울형 기초보장: 중위소득 사십팔퍼센트 이하 대상. (일인: 사십일만 원 / 사인: 백삼만구천 원)\n"
            "특징: 주거용 재산 가액에 따른 역차별 방지를 위해 소득·재산 분리 평가.\n"
            "다자녀 지원 (이자녀 이상):\n"
            "다둥이 행복카드: 공영주차장, 한강공원 등 공공시설 오십퍼센트 할인/면제.\n"
            "장기전세주택: 가점 부여 및 최장 이십년 거주, 우선 매수권 부여.\n\n"
            "생활 밀착형 서비스 (교통/중장년)\n"
            "기후동행카드 (복지권종):\n"
            "지하철·버스 삼십일권: 일반 육만이천원, 청년/다자녀 오만오천원, 저소득층 사만오천원.\n"
            "따릉이 포함 삼십일권: 일반 육만오천원, 청년/다자녀 오만팔천원, 저소득층 사만팔천원.\n"
            "중장년(사십세에서 육십사세): 오십플러스재단: 가치동행일자리(돌봄 파트너 등) 및 중장년 취업사관학교(AI 직무 교육) 운영.\n\n"
            "[서울시 자치구별 복지 상담 센터 전화번호]\n"
            "종로구: 공이-이일사팔-일일이일 / 중구: 공이-삼삼구육-일공공사\n"
            "용산구: 공이-이일구구-칠공팔팔 / 성동구: 공이-이이팔육-칠구사이\n"
            "광진구: 공이-사오공-일일사공 / 동대문구: 공이-이일이칠-오공공일\n"
            "중랑구: 공이-이공구사-일육일오 / 성북구: 공이-일오칠칠-삼일칠팔\n"
            "강북구: 공이-구공일-칠삼공공 / 도봉구: 공이-이공구일-사삼칠구\n"
            "노원구: 공이-이일일육-삼이구일 / 은평구: 공이-삼오일-팔팔팔팔\n"
            "서대문구: 공이-삼삼공-일공공사 / 마포구: 공이-삼일오삼-육이육칠\n"
            "양천구: 공이-이육이공-삼삼삼삼 / 강서구: 공이-이육공공-일일이공\n"
            "구로구: 공이-팔육공-삼공구팔 / 금천구: 공이-이육이칠-일공공사\n"
            "영등포구: 공이-이육칠공-삼구육사 / 동작구: 공이-팔이공-일팔육사\n"
            "관악구: 공이-팔칠구-오팔팔구 / 서초구: 공이-이일오오-팔삼삼구\n"
            "강남구: 공이-삼사이삼-육공이구 / 송파구: 공이-이일사칠-이칠이이\n"
            "강동구: 공이-삼사이오-오공오공"
        )

        formatted_prompt = (
            f"<|start_header_id|>system<|end_header_id|>\n\n"
            f"{system_prompt}<|eot_id|>"
            f"<|start_header_id|>user<|end_header_id|>\n\n"
            f"{text}<|eot_id|>"
            f"<|start_header_id|>assistant<|end_header_id|>\n\n"
        )

        payload = {
            "prompt": formatted_prompt,
            "stream": True,
            "n_predict": 256,
            "temperature": 0.7,
            "tts": {"enabled": True, "voiceName": "Sohee", "instruct": ""},
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

                # Agent greets first when call connects
                asyncio.create_task(process_speech("사용자가 전화를 걸었습니다. 친절하게 한국어로 인사하고 무엇을 도와드릴지 물어보세요."))

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
