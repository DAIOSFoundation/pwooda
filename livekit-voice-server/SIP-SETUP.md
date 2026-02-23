# LiveKit SIP 설정 가이드

## 서버 정보

| 항목 | 값 |
|------|-----|
| **서버 IP** | `34.64.109.59` |
| **SIP 포트 (UDP/TCP)** | `5060` |
| **SIP TLS 포트** | `5061` |
| **SIP URI** | `sip:34.64.109.59:5060` |
| **LiveKit WebSocket** | `ws://34.64.109.59:7880` |

---

## 설정 파일

### docker-compose.yaml (SIP 서비스 부분)

```yaml
# LiveKit SIP Server
livekit-sip:
  image: livekit/sip:latest
  restart: unless-stopped
  network_mode: host
  volumes:
    - ./sip-config.yaml:/sip/config.yaml:ro
  environment:
    - LIVEKIT_API_KEY=${LIVEKIT_API_KEY}
    - LIVEKIT_API_SECRET=${LIVEKIT_API_SECRET}
  depends_on:
    - livekit
```

### sip-config.yaml

```yaml
# LiveKit SIP Configuration
log_level: info

api_key: "${LIVEKIT_API_KEY}"
api_secret: "${LIVEKIT_API_SECRET}"
ws_url: "ws://localhost:7880"

redis:
  address: "localhost:6379"

sip:
  port: 5060
  port_tls: 5061
```

---

## GCP 방화벽 규칙

SIP 포트를 위해 다음 방화벽 규칙이 필요합니다:

| 이름 | 방향 | 프로토콜 | 포트 | 소스 |
|------|------|----------|------|------|
| allow-sip | Ingress | TCP, UDP | 5060, 5061 | 0.0.0.0/0 |

### gcloud 명령어

```bash
gcloud compute firewall-rules create allow-sip \
    --project=banya2025 \
    --direction=INGRESS \
    --priority=1000 \
    --network=default \
    --action=ALLOW \
    --rules=tcp:5060,tcp:5061,udp:5060,udp:5061 \
    --source-ranges=0.0.0.0/0 \
    --description="Allow SIP traffic for LiveKit"
```

---

## SIP 사업자 연동

### 인바운드 (전화 수신)

SIP 사업자에게 전달할 정보:

```
SIP Server: sip:34.64.109.59:5060
Protocol: UDP/TCP
```

인바운드 Trunk 및 Dispatch Rule 설정:

```bash
# 1. Inbound Trunk 생성
livekit-cli sip trunk create \
  --name "inbound-trunk" \
  --inbound

# 2. Dispatch Rule 생성 (전화가 오면 어느 방으로 연결할지)
livekit-cli sip dispatch-rule create \
  --name "default-rule" \
  --trunk-id "<trunk-id>" \
  --room-prefix "sip-call-"
```

### 아웃바운드 (전화 발신)

SIP 사업자에게 받아야 할 정보:

| 항목 | 설명 |
|------|------|
| SIP Trunk URI | 예: `sip:trunk.provider.com` |
| Username | 인증용 사용자명 |
| Password | 인증용 비밀번호 |
| Caller ID | 발신번호 |

아웃바운드 Trunk 설정:

```bash
livekit-cli sip trunk create \
  --name "outbound-trunk" \
  --hostname "trunk.provider.com" \
  --username "<username>" \
  --password "<password>" \
  --outbound
```

---

## 에이전트에서 전화 걸기 (Python)

```python
from livekit import api

async def make_outbound_call(phone_number: str, room_name: str):
    """아웃바운드 전화 걸기"""
    client = api.LiveKitAPI(
        url="ws://34.64.109.59:7880",
        api_key="<LIVEKIT_API_KEY>",
        api_secret="<LIVEKIT_API_SECRET>",
    )

    # SIP 참가자 생성 (전화 걸기)
    participant = await client.sip.create_sip_participant(
        api.CreateSIPParticipantRequest(
            sip_trunk_id="<trunk-id>",
            sip_call_to=f"sip:{phone_number}@trunk.provider.com",
            room_name=room_name,
            participant_identity=f"callee-{phone_number}",
        )
    )

    return participant
```

---

## 서비스 관리

```bash
# SIP 서비스 시작
docker compose up -d livekit-sip

# SIP 서비스 재시작
docker compose restart livekit-sip

# 로그 확인
docker compose logs livekit-sip --tail=50 -f

# 포트 확인
timeout 3 bash -c 'echo > /dev/tcp/34.64.109.59/5060' && echo "Port open" || echo "Port closed"
```

---

## 참고 자료

- [LiveKit SIP Documentation](https://docs.livekit.io/sip/)
- [LiveKit SIP GitHub](https://github.com/livekit/sip)
- [livekit-cli](https://github.com/livekit/livekit-cli)
