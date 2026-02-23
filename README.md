# PWOODA - AI Voice Assistant for Developmental Disabilities

AI 기반 발달장애인 생활 도움 앱

## 프로젝트 구조

```
pwooda/
├── client/                      # Android 클라이언트 앱
│   ├── app/                     # Android 앱 모듈
│   │   └── src/main/java/com/banya/neulpum/
│   │       ├── data/            # 데이터 레이어
│   │       │   ├── remote/      # API, WebSocket, LiveKit 클라이언트
│   │       │   └── repository/  # 저장소 구현
│   │       ├── domain/          # 도메인 레이어
│   │       └── presentation/    # UI 레이어 (Compose)
│   ├── build.gradle.kts         # 앱 빌드 설정
│   ├── settings.gradle.kts      # Gradle 설정
│   ├── gradlew                  # Gradle 래퍼
│   └── local.properties         # 로컬 설정 (API 키)
│
├── livekit-voice-server/        # LiveKit 기반 음성 채팅 서버
│   ├── docker-compose.yaml      # Docker 구성
│   ├── livekit.yaml             # LiveKit 서버 설정
│   ├── token-server/            # JWT 토큰 발급 서버 (FastAPI)
│   │   ├── main.py
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   ├── voice-agent/             # AI 음성 에이전트 (Python)
│   │   ├── main.py              # Agent 진입점
│   │   ├── agent.py             # Voice Agent 로직
│   │   ├── sse_client.py        # 기존 LLM/TTS 서버 연동
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   └── twilio-bridge/           # Twilio 전화 ↔ AI 에이전트 브릿지
│       ├── main.py              # FastAPI 서버 (Webhook + WebSocket)
│       ├── stt_handler.py       # 서버 측 STT (Whisper + VAD)
│       ├── audio_utils.py       # 오디오 변환 (PCM ↔ mulaw)
│       ├── requirements.txt
│       └── Dockerfile
│
└── README.md                    # 이 파일
```

## 주요 기능

### 음성 채팅 (LiveKit 기반)
- 실시간 양방향 음성 통신
- 클라이언트 측 STT (Google SpeechRecognizer)
- 서버 측 LLM + TTS 스트리밍
- 인터럽트(Barge-in) 지원

### 전화 통화 ↔ AI 에이전트 (Twilio Bridge)
- 일반 전화(PSTN)에서 AI 에이전트와 대화
- Twilio Media Streams (WebSocket) 기반
- 서버 측 STT: Faster Whisper + WebRTC VAD
- 오디오 변환: PCM 24kHz ↔ mulaw 8kHz
- SSL 지원 (Caddy 리버스 프록시)

### AI 서비스
- 개인 맞춤형 일정 관리
- 동기부여 및 행동 개선
- 안전 안내 및 생활 기술
- AI 이미지 생성

## 시작하기

### 1. Android 클라이언트 빌드

```bash
cd client

# 환경 설정 파일 생성 (local.properties)
# 아래 내용을 참고하여 설정:
# - GEMINI_API_KEY: Gemini API 키
# - TAVILY_API_KEY: Tavily 웹 검색 API 키
# - LIVEKIT_TOKEN_SERVER_URL: LiveKit 토큰 서버 URL
# - LIVEKIT_USE_LIVEKIT: LiveKit 모드 활성화 여부

# 빌드
./gradlew assembleDebug

# 설치
./gradlew installDebug
```

> **참고**: 자세한 환경 설정 방법은 [환경 변수](#환경-변수) 섹션을 참조하세요.

### 2. LiveKit 서버 실행 (로컬 테스트)

```bash
cd livekit-voice-server

# 환경 변수 설정
cp .env.example .env
# .env 파일에서 API 키 설정

# Docker Compose 실행
docker-compose up -d

# 로그 확인
docker-compose logs -f
```

### 3. 서버 접속 테스트

```bash
# LiveKit 서버 상태 확인
curl http://localhost:7880

# Token 발급 테스트
curl -X POST http://localhost:8081/api/v1/livekit/token \
  -H "Authorization: Bearer dev" \
  -H "Content-Type: application/json" \
  -d '{"room_name": "test-room", "participant_identity": "user1", "participant_name": "Test User"}'
```

## 아키텍처

### 음성 채팅 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                         서버 인프라                              │
│  ┌──────────────┐   ┌──────────────┐   ┌────────────────────┐  │
│  │ LiveKit      │   │ Token Server │   │ Voice Agent        │  │
│  │ Server       │   │ (FastAPI)    │   │ (Python)           │  │
│  │              │◄──┤              │   │                    │  │
│  │ - WebRTC     │   │ - JWT 발급   │   │ - Data Channel     │  │
│  │ - 미디어     │   │ - 인증 연동  │   │   메시지 수신      │  │
│  │   라우팅     │   │              │   │ - LLM/TTS 호출     │  │
│  └──────┬───────┘   └──────────────┘   │ - 오디오 스트리밍  │  │
│         │                              └─────────┬──────────┘  │
│         │                                        │             │
│         │              ┌─────────────────────────▼───────┐     │
│         │              │ 기존 LLM/TTS 서버               │     │
│         │              │ (SSE: /completion-with-tts)     │     │
│         │              └─────────────────────────────────┘     │
└─────────┼──────────────────────────────────────────────────────┘
          │ WebRTC
          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Android 클라이언트                          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    VoiceChatScreen                         │ │
│  │  - Google STT (클라이언트 측 음성 인식)                    │ │
│  │  - LiveKitRoomManager (Room 연결/관리)                     │ │
│  │  - Data Channel (텍스트 전송, 인터럽트)                    │ │
│  │  - AI 오디오 트랙 자동 재생                                │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### 데이터 흐름

1. **음성 입력**: 사용자 음성 → Google STT → 텍스트
2. **메시지 전송**: 텍스트 → Data Channel → Voice Agent
3. **LLM 처리**: Agent → 기존 SSE 서버 → 응답 생성
4. **음성 출력**: TTS 오디오 → LiveKit → 클라이언트 재생

### 상세 호출 흐름

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              호출 흐름                                   │
└─────────────────────────────────────────────────────────────────────────┘

1. 토큰 요청
   Android ──HTTP POST──► Token Server ──► JWT 토큰 반환
                          (localhost:8081)

2. Room 연결
   Android ──WebRTC──► LiveKit Server ◄── Voice Agent 참여
                       (localhost:7880)

3. 음성 인식 (클라이언트 측)
   사용자 음성 ──► Google STT ──► 텍스트

4. 텍스트 전송
   Android ──Data Channel──► Voice Agent
             (LiveKit 경유)

5. LLM/TTS 호출
   Voice Agent ──HTTP/SSE──► 기존 LLM-TTS 서버
                              (210.109.53.87/completion-with-tts)

   응답 형식:
   {"type": "text", "content": "..."}
   {"type": "audio", "audio": "base64...", "sentenceId": 1}
   {"type": "done"}

6. 오디오 스트리밍
   Voice Agent ──Audio Track──► LiveKit Server ──► Android
                (WebRTC)                          (자동 재생)
```

### 통신 프로토콜 요약

| 구간 | 프로토콜 | 설명 |
|------|----------|------|
| Android → Token Server | HTTP | JWT 토큰 발급 |
| Android ↔ LiveKit | WebRTC | Room 연결, 오디오 수신 |
| Android → Agent | Data Channel | 텍스트 전송 (STT 결과) |
| Agent → LLM-TTS 서버 | HTTP/SSE | 기존 서버 호출 |
| Agent → Android | Audio Track | TTS 오디오 스트리밍 |

## 기술 스택

### Android 클라이언트
- **언어**: Kotlin
- **UI**: Jetpack Compose
- **아키텍처**: MVVM
- **실시간 통신**: LiveKit Android SDK
- **음성 인식**: Android SpeechRecognizer
- **네트워크**: OkHttp, Retrofit

### LiveKit 서버
- **미디어 서버**: LiveKit Server (Docker)
- **토큰 서버**: FastAPI (Python)
- **Voice Agent**: LiveKit Agents Framework (Python)
- **STT/LLM/TTS**: 기존 SSE 서버 연동

### Twilio Bridge
- **웹 서버**: FastAPI (Python)
- **STT**: Faster Whisper (base 모델) + WebRTC VAD
- **전화 연동**: Twilio Voice API + Media Streams
- **SSL**: Caddy (자동 Let's Encrypt 인증서)

## 환경 변수

### Android 클라이언트 설정 (client/local.properties)

Android 앱의 환경 설정은 `client/local.properties` 파일에서 관리합니다.
이 파일은 Git에 포함되지 않으므로 직접 생성해야 합니다.

```properties
# Android SDK 경로 (Android Studio가 자동 생성)
sdk.dir=/path/to/Android/sdk

# AI 서비스 API 키
GEMINI_API_KEY=your_gemini_api_key
TAVILY_API_KEY=your_tavily_api_key

# LiveKit 설정
# 로컬 개발: http://10.0.2.2:8081 (에뮬레이터에서 localhost 접근)
# 실제 기기: http://your-pc-ip:8081
# 프로덕션: https://your-server.com
LIVEKIT_TOKEN_SERVER_URL=http://10.0.2.2:8081

# LiveKit 모드 활성화 (true/false)
# true: LiveKit 음성 채팅 사용
# false: 기존 WebSocket 음성 채팅 사용
LIVEKIT_USE_LIVEKIT=true
```

#### 환경별 설정 예시

**로컬 개발 (에뮬레이터)**
```properties
LIVEKIT_TOKEN_SERVER_URL=http://10.0.2.2:8081
LIVEKIT_USE_LIVEKIT=true
```

**로컬 개발 (실제 기기)**
```properties
# PC의 실제 IP 주소 사용 (예: 192.168.1.100)
LIVEKIT_TOKEN_SERVER_URL=http://192.168.1.100:8081
LIVEKIT_USE_LIVEKIT=true
```

**프로덕션 (GCP)**
```properties
LIVEKIT_TOKEN_SERVER_URL=http://34.64.224.230:8081
LIVEKIT_USE_LIVEKIT=true
```

**LiveKit 비활성화 (기존 WebSocket 모드)**
```properties
LIVEKIT_USE_LIVEKIT=false
```

### LiveKit 서버 설정 (livekit-voice-server/.env)

LiveKit 서버의 환경 설정은 `livekit-voice-server/.env` 파일에서 관리합니다.
`.env.example` 파일을 복사하여 사용하세요.

```bash
# LiveKit 서버 인증 (docker-compose에서 사용)
LIVEKIT_API_KEY=devkey
LIVEKIT_API_SECRET=secret1234567890

# LiveKit 서버 URL (Token Server가 클라이언트에 반환)
# 실제 기기 테스트 시 PC의 IP 사용 (예: ws://192.168.1.100:7880)
LIVEKIT_URL=ws://192.168.1.100:7880

# 인증 서버 URL (Token Server에서 access_token 검증용)
AUTH_SERVER_URL=https://api-llmops.banya.ai

# LLM-TTS 서버 URL (Voice Agent가 호출)
# 포트 8083 필수!
SSE_SERVER_URL=http://210.109.53.87:8083/completion-with-tts
SSE_AUTH_TOKEN=your_sse_auth_token

# Twilio 설정 (전화 ↔ AI 에이전트)
TWILIO_ACCOUNT_SID=your_twilio_account_sid
TWILIO_AUTH_TOKEN=your_twilio_auth_token
TWILIO_PHONE_NUMBER=+1XXXXXXXXXX
TWILIO_WEBHOOK_BASE_URL=https://twilio.yourdomain.com
```

### LiveKit 서버 설정 (livekit-voice-server/livekit.yaml)

LiveKit 미디어 서버의 설정 파일입니다.

```yaml
port: 7880

rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 50100
  use_external_ip: false  # 로컬 개발용

redis:
  address: redis:6379

room:
  auto_create: true
  empty_timeout: 300

# Agent 설정 (필수!)
# 이 설정이 없으면 Voice Agent가 Room에 자동 참가하지 않음
agent:
  enabled: true
  region: ""

turn:
  enabled: false  # 로컬 개발용
```

**주요 설정 항목:**
- `agent.enabled: true`: Voice Agent가 Room에 자동 참가하도록 활성화 (필수)
- `room.auto_create: true`: 참가자가 연결 시 Room 자동 생성
- `rtc.port_range_*`: WebRTC 미디어용 UDP 포트 범위

## 방화벽 포트 (프로덕션)

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 443 | TCP | HTTPS/WSS |
| 7880 | TCP | LiveKit HTTP API |
| 7881 | TCP | WebRTC over TCP |
| 3478 | UDP | TURN/UDP |
| 5349 | TCP | TURN/TLS |
| 50000-50100 | UDP | WebRTC 미디어 |
| 80 | TCP | HTTP (Caddy SSL 인증서 발급) |
| 8082 | TCP | Twilio Bridge (내부, Caddy 경유) |

## Twilio 전화 연동 설정

### 전화 통화 흐름

```
일반 전화기 ──PSTN──► Twilio ──Webhook──► Caddy (SSL) ──► Twilio Bridge (:8082)
                                                              │
                                                              ├─ STT (Whisper)
                                                              ├─ SSE 서버 (LLM + TTS)
                                                              └─ 오디오 응답 ──► Twilio ──► 전화기
```

### Twilio 콘솔 설정

1. Phone Numbers → Active Numbers → 번호 선택
2. Voice Configuration:
   - **A call comes in**: Webhook
   - **URL**: `https://twilio.banya.ai/twilio/voice`
   - **HTTP Method**: HTTP POST

### Caddy SSL 리버스 프록시

```
# /etc/caddy/Caddyfile
twilio.banya.ai {
    reverse_proxy localhost:8082
}
```

Caddy가 자동으로 Let's Encrypt SSL 인증서를 발급하고 갱신합니다.
Twilio Media Streams는 `wss://` (SSL WebSocket)만 지원하므로 SSL이 필수입니다.

### 주의사항

- Twilio **Trial 계정**은 Verified Caller IDs에 등록된 번호에서만 전화 가능
- 모든 번호에서 전화 수신하려면 Twilio 계정 업그레이드 필요
- SSE 서버(`210.109.53.87:8083`)가 가동 중이어야 AI 응답 가능

## 변경 이력

### 2026-02-23
- **GCP 프로덕션 서버 배포** (`34.64.224.230`, `live-call-agent-svr`)
  - LiveKit Server v1.6.0, Redis, Token Server, Voice Agent, Twilio Bridge 가동
  - `livekit.yaml`: `use_external_ip: true` 프로덕션 설정 적용
  - LiveKit v1.6.0에서 `agent` 필드가 yaml에서 지원되지 않음 확인 (환경변수로 처리)
- **Twilio 전화 ↔ AI 에이전트 연동 배포**
  - Twilio Bridge 서비스 추가 (`:8082`, FastAPI + Whisper STT)
  - Twilio Voice Webhook: `https://twilio.banya.ai/twilio/voice`
  - Twilio Media Streams WebSocket: `wss://twilio.banya.ai/twilio/stream`
- **Caddy SSL 리버스 프록시 설정**
  - `twilio.banya.ai` → `localhost:8082` 리버스 프록시
  - Let's Encrypt 자동 SSL 인증서 발급
  - Twilio Media Streams가 `wss://`만 지원하므로 SSL 필수
- **GCP 방화벽 포트 추가**: 80(HTTP), 443(HTTPS), 8082(Twilio Bridge)
- **프로덕션 서버 IP 변경**: `LIVEKIT_TOKEN_SERVER_URL`을 GCP 신규 인스턴스로 변경
  - 기존: `http://34.64.109.59:8081`
  - 변경: `http://34.64.224.230:8081`

### 2026-02-03
- **Token Server TTL 수정**: `token.with_ttl(3600)` → `token.with_ttl(timedelta(hours=1))`로 변경
  - livekit-api 라이브러리의 TTL 파라미터는 `timedelta` 객체를 요구함
- **실제 기기 테스트 설정 추가**:
  - `client/local.properties`: 에뮬레이터용 `10.0.2.2` 대신 PC의 실제 IP 사용
  - `livekit-voice-server/.env`: `LIVEKIT_URL`을 실제 IP로 변경 (Android 기기에서 `localhost` 접근 불가)
- **Voice Agent Dockerfile 수정**: `libglib2.0-0`, `libgobject-2.0-0` 라이브러리 추가
- **LiveKit Android SDK 버전 수정**:
  - `io.livekit:livekit-android:2.11.0`
  - `io.livekit:livekit-android-compose-components:1.4.0`
- **JitPack 저장소 추가**: `client/settings.gradle.kts`에 LiveKit 의존성용 JitPack 추가
- **Docker Compose 수정**: LIVEKIT_KEYS 환경변수 형식 수정 (`key: secret` 형태로)
- **livekit.yaml Agent 설정 추가**: Agent가 Room에 자동 참가하도록 `agent.enabled: true` 추가
- **Voice Agent 이벤트 핸들러 수정**: async 콜백을 sync 래퍼로 감싸서 등록 (livekit-rtc 호환성)
- **Voice Agent DataPacket 시그니처 수정**: `data_received` 이벤트가 `DataPacket` 객체를 전달하도록 수정
- **SSE 서버 URL 포트 수정**: `http://210.109.53.87/completion-with-tts` → `http://210.109.53.87:8083/completion-with-tts`

### 실제 기기 테스트 체크리스트

1. PC의 IP 주소 확인 (예: `ifconfig | grep inet`)
2. `client/local.properties`에서 `LIVEKIT_TOKEN_SERVER_URL` 업데이트
3. `livekit-voice-server/.env`에서 `LIVEKIT_URL` 업데이트
4. Docker 컨테이너 재시작: `docker-compose up -d`
5. 포트 접근 확인:
   - `nc -zv <PC_IP> 8081` (Token Server)
   - `nc -zv <PC_IP> 7880` (LiveKit Server)

## 라이선스

Private - DAIOS Foundation
