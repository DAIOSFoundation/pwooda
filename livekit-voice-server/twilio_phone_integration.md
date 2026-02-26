# Twilio 전화 통화 ↔ AI 에이전트 연동 가이드

Twilio를 통해 실제 전화 통화로 AI 에이전트(Gemini 기반)와 대화할 수 있는 기능입니다.

## 아키텍처

```
[아웃바운드 콜] - 앱에서 버튼 → 서버 API → Twilio가 사용자에게 전화
┌──────────────┐  API 호출   ┌─────────────────┐  Twilio REST   ┌───────────┐
│ Android App  │ ──────────▶ │ twilio-bridge   │ ────────────▶ │ Twilio    │
│ "AI 전화"    │             │ /call/initiate  │               │ PSTN      │
└──────────────┘             └─────────────────┘               └─────┬─────┘
                                                                     │ 전화
                                                                     ▼
                                                              +8201062904539

[인바운드 콜] - 사용자가 Twilio 번호로 직접 전화
┌──────────────┐  전화 걸기  ┌───────────┐  Webhook   ┌─────────────────┐
│ 사용자 전화  │ ──────────▶ │ Twilio    │ ────────▶ │ twilio-bridge   │
│              │ +1831...    │ PSTN      │           │ /twilio/voice   │
└──────────────┘             └───────────┘           └─────────────────┘

[공통 흐름] - TwiML <Connect><Stream> → WebSocket 양방향 오디오
┌─────────────────────────────────────────────────────────────────────┐
│                    twilio-bridge 서비스 (PORT 8082)                  │
│                                                                     │
│  WebSocket /twilio/stream                                           │
│  ┌──────────┐    ┌──────────┐    ┌────────────────────────────────┐ │
│  │ mulaw→PCM │──▶│ VAD+STT  │──▶│ SSE Client (LLM+TTS)         │ │
│  │ PCM→mulaw │◀──│ Whisper  │   │ → eu6l8rgf-8083.thundercompute.net │ │
│  └──────────┘    └──────────┘    └────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

## Twilio 계정 정보

| 항목 | 값 |
|------|-----|
| Account SID | `your-twilio-account-sid` |
| Auth Token | (Twilio Console에서 확인) |
| 전화번호 | `+18312733281` |
| 대상 번호 | `+8201062904539` |

## 파일 구조

```
livekit-voice-server/
├── twilio-bridge/              ← 신규 서비스
│   ├── main.py                 # FastAPI 앱 (웹훅 + WebSocket + 콜 API)
│   ├── audio_utils.py          # 오디오 포맷 변환 (mulaw ↔ PCM)
│   ├── stt_handler.py          # VAD (webrtcvad) + faster-whisper 한국어 STT
│   ├── requirements.txt        # Python 의존성
│   └── Dockerfile              # 컨테이너 빌드
├── docker-compose.yaml         ← 수정 (twilio-bridge 서비스 추가)
└── .env                        ← 수정 (Twilio 환경변수 추가)
```

## API 엔드포인트

### 1. `POST /twilio/voice` (Twilio 웹훅)

Twilio가 전화 연결 시 호출. TwiML 응답 반환:

```xml
<Response>
  <Say language="ko-KR">안녕하세요, AI 에이전트에 연결합니다.</Say>
  <Connect>
    <Stream url="wss://서버주소/twilio/stream" />
  </Connect>
</Response>
```

### 2. `WebSocket /twilio/stream` (Twilio Media Streams)

양방향 오디오 스트리밍 처리:
- **수신**: Twilio → mulaw 8kHz 오디오 → PCM 변환 → VAD → STT (Whisper)
- **송신**: STT 텍스트 → SSE 서버 (LLM+TTS) → TTS 오디오 → mulaw 변환 → Twilio

### 3. `POST /api/v1/call/initiate` (아웃바운드 콜)

Android 앱에서 호출하여 Twilio 아웃바운드 콜 시작:

```json
// Request
{ "to": "+8201062904539" }

// Response
{ "success": true, "call_sid": "CA...", "to": "+8201062904539", "from": "+18312733281" }
```

### 4. `GET /health` (헬스체크)

```json
{ "status": "ok", "service": "twilio-bridge" }
```

## 오디오 처리 흐름

```
Twilio (mulaw 8kHz) → PCM 16bit 8kHz → 리샘플 16kHz → Whisper STT
                                                            │
                                                            ▼ 텍스트
                                                     SSE 서버 (LLM+TTS)
                                                            │
                                                            ▼ PCM 24kHz
Twilio (mulaw 8kHz) ← mulaw 인코딩 ← 리샘플 8kHz ← TTS 오디오
```

## STT (음성 인식) 상세

- **VAD**: `webrtcvad` (Voice Activity Detection)
  - 20ms 프레임 단위로 음성/무음 판별
  - 800ms 무음 감지 시 음성 구간 종료로 판단
- **STT**: `faster-whisper` (base 모델)
  - 한국어 (`ko`) 설정
  - CPU 모드 (int8 양자화)
  - 최소 0.5초 이상 오디오만 전사

## 환경변수 (.env)

```bash
# Twilio 설정
TWILIO_ACCOUNT_SID=your-twilio-account-sid
TWILIO_AUTH_TOKEN=your-twilio-auth-token-here
TWILIO_PHONE_NUMBER=+18312733281
TWILIO_WEBHOOK_BASE_URL=http://34.64.109.59:8082
```

## 배포 절차

### 1. GCP 방화벽 규칙 추가

```bash
gcloud compute firewall-rules create twilio-bridge \
  --allow=tcp:8082 \
  --target-tags=livekit-server \
  --description="Twilio bridge service"
```

### 2. .env 파일에 실제 Twilio Auth Token 설정

```bash
# GCP 서버에서
cd ~/pwooda/livekit-voice-server
nano .env
# TWILIO_AUTH_TOKEN=실제토큰값
```

### 3. Docker 빌드 및 실행

```bash
docker compose up -d --build twilio-bridge
docker compose logs -f twilio-bridge
```

### 4. Twilio Console 설정 (인바운드 콜용)

1. [Twilio Console](https://console.twilio.com) 접속
2. Phone Numbers > Active Numbers > +18312733281 선택
3. Voice Configuration:
   - **A CALL COMES IN**: Webhook
   - **URL**: `http://34.64.109.59:8082/twilio/voice`
   - **HTTP Method**: POST

## Android 앱 사용 방법

### AI 전화 (아웃바운드)
1. 음성 채팅 화면에서 **"AI 전화"** 버튼 (녹색) 클릭
2. 서버가 Twilio API를 통해 +8201062904539로 전화
3. 전화 수신 → 통화 → AI 에이전트와 대화

### 직접 전화 (인바운드)
1. 음성 채팅 화면에서 **"직접 전화"** 버튼 (파란색) 클릭
2. 전화 다이얼러에 +18312733281 자동 입력
3. 전화 걸기 → AI 에이전트와 대화

## 테스트 방법

### 서비스 상태 확인

```bash
curl http://34.64.109.59:8082/health
```

### 아웃바운드 콜 테스트 (curl)

```bash
curl -X POST http://34.64.109.59:8082/api/v1/call/initiate \
  -H "Content-Type: application/json" \
  -d '{"to": "+8201062904539"}'
```

### 로그 확인

```bash
docker compose logs -f twilio-bridge
```

## SSL 설정 (권장)

Twilio는 HTTPS 웹훅을 권장합니다. Caddy를 사용한 SSL 설정:

```
# /etc/caddy/Caddyfile
twilio.yourdomain.com {
    reverse_proxy localhost:8082
}
```

SSL 적용 후 환경변수 업데이트:
```bash
TWILIO_WEBHOOK_BASE_URL=https://twilio.yourdomain.com
```

Twilio Console의 webhook URL도 업데이트:
```
https://twilio.yourdomain.com/twilio/voice
```

## 트러블슈팅

### 전화가 오지 않음
- `TWILIO_AUTH_TOKEN`이 올바르게 설정되었는지 확인
- Twilio 계정 잔액 확인 (Trial 계정은 verified 번호만 가능)
- 방화벽에서 8082 포트 개방 확인

### AI 응답이 없음
- `SSE_SERVER_URL`, `SSE_AUTH_TOKEN` 확인
- SSE 서버 상태 확인: `curl https://eu6l8rgf-8083.thundercompute.net/health`
- twilio-bridge 로그에서 STT 결과 확인

### 음성 인식이 안됨
- Whisper 모델 로드 확인 (Dockerfile에서 빌드 시 다운로드)
- VAD sensitivity 조정 (stt_handler.py의 `vad_aggressiveness` 0~3)
- 오디오 포맷 변환 확인 (mulaw → PCM)

### WebSocket 연결 실패
- `TWILIO_WEBHOOK_BASE_URL`이 공인 IP/도메인인지 확인
- Twilio가 서버에 접근 가능한지 확인
- WSS(SSL) 사용 시 인증서 유효성 확인

## 비용 예상

| 항목 | 비용 |
|------|------|
| Twilio 전화번호 | ~$1/월 |
| 아웃바운드 통화 (한국) | ~$0.013/분 |
| 인바운드 통화 | ~$0.0085/분 |
| **월 100분 사용 시** | **~$2.5/월 (약 3,300원)** |
