# 가상 전화번호 테스트 구현 가이드

LiveKit SIP 연동을 통해 실제 전화번호로 AI 음성 채팅을 테스트하는 방법입니다.

## 개요

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   일반 전화기    │────▶│   SIP Provider  │────▶│   LiveKit SIP   │
│  (휴대폰/유선)   │     │  (Twilio 등)    │     │    Gateway      │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │   Voice Agent   │
                                                │  (AI 응답 생성)  │
                                                └─────────────────┘
```

## 1. SIP Provider 선택

### 옵션 1: Twilio (권장)
- **장점**: 한국 전화번호 지원, 문서 풍부, LiveKit 공식 지원
- **비용**: 전화번호 ~$1/월, 통화료 ~$0.01/분
- **가입**: https://www.twilio.com

### 옵션 2: Telnyx
- **장점**: 저렴한 가격, SIP trunk 전문
- **비용**: 전화번호 ~$1/월, 통화료 ~$0.005/분
- **가입**: https://telnyx.com

### 옵션 3: Vonage (Nexmo)
- **장점**: 글로벌 커버리지
- **가입**: https://www.vonage.com

## 2. Twilio 설정 (상세)

### 2.1 계정 생성 및 전화번호 구매

1. https://www.twilio.com 가입
2. Console > Phone Numbers > Buy a Number
3. 한국 번호 선택 (Voice 기능 활성화 확인)
4. 구매 완료

### 2.2 SIP Trunk 생성

1. Console > Elastic SIP Trunking > Trunks > Create
2. Trunk 이름 입력 (예: `livekit-trunk`)
3. Origination 설정:
   ```
   Origination URI: sip:your-livekit-server.com:5060
   Weight: 10
   Priority: 10
   ```

### 2.3 인증 정보 확인

Console > Account > API Keys에서 확인:
- **Account SID**: `ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`
- **Auth Token**: `xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

## 3. LiveKit SIP 설정

### 3.1 livekit.yaml 수정

```yaml
# livekit.yaml
port: 7880

rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 50100
  use_external_ip: true  # 프로덕션에서 필수

redis:
  address: redis:6379

room:
  auto_create: true
  empty_timeout: 300

agent:
  enabled: true
  region: ""

# SIP 설정 추가
sip:
  enabled: true
  # SIP 서버 포트
  port: 5060
  # SIP over TLS 포트 (선택)
  tls_port: 5061
```

### 3.2 docker-compose.yaml 수정

```yaml
version: '3.8'

services:
  livekit:
    image: livekit/livekit-server:latest
    ports:
      - "7880:7880"      # HTTP API
      - "7881:7881"      # WebRTC TCP
      - "5060:5060/udp"  # SIP UDP
      - "5060:5060/tcp"  # SIP TCP
      - "5061:5061/tcp"  # SIP TLS
      - "50000-50100:50000-50100/udp"  # RTP
    volumes:
      - ./livekit.yaml:/etc/livekit.yaml
    command: --config /etc/livekit.yaml
    environment:
      - LIVEKIT_KEYS=${LIVEKIT_API_KEY}:${LIVEKIT_API_SECRET}
```

### 3.3 환경변수 추가 (.env)

```bash
# 기존 설정
LIVEKIT_API_KEY=devkey
LIVEKIT_API_SECRET=secret1234567890
LIVEKIT_URL=wss://your-domain.com

# Twilio SIP 설정
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_PHONE_NUMBER=+821012345678
```

## 4. SIP Trunk 등록 (Python)

### 4.1 SIP Trunk 생성 스크립트

```python
# scripts/create_sip_trunk.py
import asyncio
from livekit import api

async def create_sip_trunk():
    lk_api = api.LiveKitAPI(
        url="https://your-livekit-server.com",
        api_key="your-api-key",
        api_secret="your-api-secret"
    )

    # Twilio SIP Trunk 생성
    trunk = await lk_api.sip.create_sip_trunk(
        api.CreateSIPTrunkRequest(
            name="twilio-trunk",
            # Twilio Termination URI
            address="your-trunk.pstn.twilio.com",
            # 인바운드 번호 (전화 수신용)
            numbers=["+821012345678"],
            # 인증 정보
            auth_username="your-twilio-username",
            auth_password="your-twilio-password",
            # 인바운드 설정
            inbound_addresses=["54.172.60.0/30"],  # Twilio IP ranges
        )
    )

    print(f"Created trunk: {trunk.sip_trunk_id}")
    return trunk

asyncio.run(create_sip_trunk())
```

### 4.2 Dispatch Rule 생성 (인바운드 통화 라우팅)

```python
# scripts/create_dispatch_rule.py
import asyncio
from livekit import api

async def create_dispatch_rule():
    lk_api = api.LiveKitAPI(
        url="https://your-livekit-server.com",
        api_key="your-api-key",
        api_secret="your-api-secret"
    )

    # 인바운드 통화를 특정 Room으로 라우팅
    rule = await lk_api.sip.create_sip_dispatch_rule(
        api.CreateSIPDispatchRuleRequest(
            name="voice-agent-rule",
            # 전화가 오면 이 Room으로 연결
            rule=api.SIPDispatchRule(
                dispatch_rule_direct=api.SIPDispatchRuleDirect(
                    room_name="voice-chat-room",
                    pin=""  # PIN 없이 바로 연결
                )
            ),
            # 이 번호로 온 전화에만 적용
            trunk_ids=["trunk-id-here"],
            inbound_numbers=["+821012345678"],
        )
    )

    print(f"Created dispatch rule: {rule.sip_dispatch_rule_id}")
    return rule

asyncio.run(create_dispatch_rule())
```

## 5. Voice Agent SIP 지원 추가

### 5.1 agent.py 수정

```python
# agent.py - SIP 참가자 감지 추가

class VoiceAgent:
    async def start(self, participant: rtc.RemoteParticipant):
        """Start the voice agent for a participant."""
        logger.info(f"Starting voice agent for {participant.identity}")

        # SIP 참가자인지 확인
        is_sip = participant.kind == rtc.ParticipantKind.SIP
        if is_sip:
            logger.info(f"SIP participant connected: {participant.identity}")
            # SIP 통화의 경우 Data Channel 대신 오디오 직접 처리
            self._setup_sip_audio_handler(participant)
        else:
            # 기존 Data Channel 방식
            self._setup_data_channel_handler(participant)

        # ... 나머지 코드

    def _setup_sip_audio_handler(self, participant: rtc.RemoteParticipant):
        """SIP 참가자용 오디오 핸들러 설정"""
        # SIP 통화는 서버 측 STT 필요
        # Whisper 또는 Google STT 사용
        pass
```

### 5.2 서버 측 STT 추가 (SIP용)

SIP 통화는 클라이언트 측 STT가 없으므로 서버에서 STT 처리 필요:

```python
# stt_handler.py
import asyncio
from livekit import rtc
from faster_whisper import WhisperModel

class STTHandler:
    def __init__(self):
        # Whisper 모델 로드
        self.model = WhisperModel("base", device="cpu")
        self.audio_buffer = []

    async def process_audio_frame(self, frame: rtc.AudioFrame):
        """오디오 프레임을 버퍼에 추가하고 STT 처리"""
        self.audio_buffer.append(frame.data)

        # 2초 분량이 모이면 STT 실행
        if len(self.audio_buffer) >= 100:  # 약 2초
            audio_data = b''.join(self.audio_buffer)
            self.audio_buffer = []

            # Whisper STT
            segments, _ = self.model.transcribe(audio_data)
            text = " ".join([s.text for s in segments])

            return text
        return None
```

## 6. 아웃바운드 통화 (AI가 전화 거는 기능)

```python
# scripts/outbound_call.py
import asyncio
from livekit import api

async def make_outbound_call(phone_number: str, room_name: str):
    """AI가 사용자에게 전화를 거는 기능"""
    lk_api = api.LiveKitAPI(
        url="https://your-livekit-server.com",
        api_key="your-api-key",
        api_secret="your-api-secret"
    )

    # SIP 참가자 생성 (전화 걸기)
    participant = await lk_api.sip.create_sip_participant(
        api.CreateSIPParticipantRequest(
            room_name=room_name,
            sip_trunk_id="your-trunk-id",
            # 전화할 번호
            sip_call_to=phone_number,
            # 발신자 표시 번호
            sip_number="+821012345678",
            # 참가자 이름
            participant_name="AI Assistant",
            # 참가자 메타데이터
            participant_metadata='{"type": "ai_outbound"}',
        )
    )

    print(f"Call initiated: {participant.participant_id}")
    return participant

# 사용 예시
asyncio.run(make_outbound_call("+821098765432", "ai-call-room"))
```

## 7. 테스트 방법

### 7.1 인바운드 테스트 (전화 받기)

1. 모든 서버 실행:
   ```bash
   cd livekit-voice-server
   docker-compose up -d
   ```

2. SIP Trunk 및 Dispatch Rule 생성:
   ```bash
   python scripts/create_sip_trunk.py
   python scripts/create_dispatch_rule.py
   ```

3. 휴대폰에서 구매한 가상 번호로 전화

4. 로그 확인:
   ```bash
   docker-compose logs -f voice-agent
   ```

### 7.2 아웃바운드 테스트 (전화 걸기)

```bash
python scripts/outbound_call.py
```

## 8. 방화벽 설정

프로덕션 환경에서 필요한 포트:

| 포트 | 프로토콜 | 용도 |
|------|----------|------|
| 5060 | UDP/TCP | SIP Signaling |
| 5061 | TCP | SIP TLS |
| 10000-20000 | UDP | RTP Media |
| 7880 | TCP | LiveKit HTTP |
| 7881 | TCP | WebRTC TCP |

## 9. 비용 계산 예시

### Twilio 기준 (한국)
- 전화번호: $1/월
- 인바운드 통화: $0.0085/분
- 아웃바운드 통화: $0.013/분

### 월 100분 사용 시
- 전화번호: $1
- 통화료: ~$1.3
- **총: 약 $2.3/월 (약 3,000원)**

## 10. 주의사항

1. **프로덕션 배포 필요**: SIP는 공인 IP가 필요하므로 로컬에서 테스트 불가
2. **SSL 인증서**: WSS/TLS 연결을 위해 유효한 SSL 인증서 필요
3. **Twilio IP 허용**: Twilio의 IP 범위를 방화벽에서 허용해야 함
4. **서버 측 STT**: SIP 통화는 서버에서 음성 인식 처리 필요

## 11. 다음 단계

1. [ ] 프로덕션 서버 배포 (AWS/GCP)
2. [ ] SSL 인증서 설정
3. [ ] Twilio 계정 생성 및 전화번호 구매
4. [ ] SIP Trunk 설정
5. [ ] 서버 측 STT (Whisper) 통합
6. [ ] 아웃바운드 통화 기능 구현
