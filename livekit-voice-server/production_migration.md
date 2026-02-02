# LiveKit Voice Server 프로덕션 마이그레이션 가이드

로컬 개발 환경에서 GCP 클라우드로 이관하는 방법을 설명합니다.

## 1. 서버 사양 권장

### 최소 사양 (테스트/소규모)

| 항목 | 사양 |
|------|------|
| **CPU** | 2 vCPU |
| **RAM** | 4 GB |
| **Storage** | 20 GB SSD |
| **Network** | 100 Mbps |
| **동시 사용자** | ~5명 |

### 권장 사양 (프로덕션)

| 항목 | 사양 |
|------|------|
| **CPU** | 4 vCPU |
| **RAM** | 8 GB |
| **Storage** | 40 GB SSD |
| **Network** | 1 Gbps |
| **동시 사용자** | ~20명 |

### 클라우드 인스턴스 예시

| 클라우드 | 최소 | 권장 |
|----------|------|------|
| **AWS** | t3.medium | t3.large / c5.xlarge |
| **GCP** | e2-medium | e2-standard-4 |
| **Azure** | B2s | D4s_v3 |

### 비용 예상 (월)
- 최소 사양: ~$20-30/월
- 권장 사양: ~$50-80/월

### 주요 고려사항
1. **CPU** - LiveKit이 WebRTC 미디어 처리에 CPU 사용
2. **네트워크** - 음성 스트리밍에 안정적인 대역폭 필요
3. **포트 개방** - UDP 50000-50100 (RTP), TCP 7880-7881

---

## 2. GCP 마이그레이션 절차

### 2.1 GCP 인스턴스 생성

```bash
# 권장 인스턴스 (서울 리전)
gcloud compute instances create livekit-server \
  --zone=asia-northeast3-a \
  --machine-type=e2-standard-4 \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=40GB \
  --tags=livekit-server
```

### 2.2 방화벽 규칙 설정

```bash
# LiveKit 포트 개방
gcloud compute firewall-rules create livekit-ports \
  --allow=tcp:7880,tcp:7881,tcp:8081,udp:50000-50100 \
  --target-tags=livekit-server \
  --description="LiveKit server ports"
```

### 2.3 서버 초기 설정

```bash
# SSH 접속
gcloud compute ssh livekit-server --zone=asia-northeast3-a

# Docker 설치
sudo apt-get update
sudo apt-get install -y docker.io docker-compose-plugin
sudo usermod -aG docker $USER

# 재로그인 필요 (logout 후 다시 ssh)
exit
gcloud compute ssh livekit-server --zone=asia-northeast3-a
```

### 2.4 프로젝트 배포

```bash
# 코드 클론
git clone https://github.com/DAIOSFoundation/pwooda.git
cd pwooda/livekit-voice-server

# 환경변수 설정
cp .env.example .env
nano .env
```

### 2.5 환경변수 설정 (.env)

```bash
# 프로덕션용 보안 키 생성
# openssl rand -hex 16 으로 생성 권장
LIVEKIT_API_KEY=your-production-api-key
LIVEKIT_API_SECRET=your-secure-32-char-secret-here

# 외부 접속 URL (도메인 또는 외부 IP)
# 도메인 사용 시: wss://livekit.yourdomain.com
# IP 사용 시: ws://EXTERNAL_IP:7880
LIVEKIT_URL=wss://livekit.yourdomain.com

# 인증 서버 (기존 유지)
AUTH_SERVER_URL=https://api-llmops.banya.ai

# LLM-TTS 서버 (기존 유지)
SSE_SERVER_URL=http://210.109.53.87:8083/completion-with-tts
SSE_AUTH_TOKEN=your-sse-auth-token-here
```

### 2.6 LiveKit 설정 수정 (livekit.yaml)

```yaml
port: 7880

rtc:
  tcp_port: 7881
  port_range_start: 50000
  port_range_end: 50100
  use_external_ip: true  # 프로덕션에서 필수!

redis:
  address: redis:6379

room:
  auto_create: true
  empty_timeout: 300

agent:
  enabled: true
  region: ""

# TURN 서버 (NAT 환경에서 필요할 수 있음)
turn:
  enabled: false  # 필요시 true로 변경
```

### 2.7 Docker 실행

```bash
# 백그라운드 실행
docker compose up -d

# 로그 확인
docker compose logs -f

# 상태 확인
docker compose ps
```

### 2.8 SSL/도메인 설정 (선택사항)

#### Caddy 사용 (자동 SSL)

```bash
# Caddy 설치
sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt-get update
sudo apt-get install caddy

# Caddyfile 설정
sudo nano /etc/caddy/Caddyfile
```

```
# /etc/caddy/Caddyfile
livekit.yourdomain.com {
    reverse_proxy localhost:7880
}

api.yourdomain.com {
    reverse_proxy localhost:8081
}
```

```bash
# Caddy 재시작
sudo systemctl restart caddy
```

---

## 3. 클라이언트 설정 변경

### Android (local.properties)

```properties
# IP 직접 사용 시
LIVEKIT_TOKEN_SERVER_URL=http://GCP_EXTERNAL_IP:8081

# 도메인 사용 시
LIVEKIT_TOKEN_SERVER_URL=https://api.yourdomain.com

LIVEKIT_USE_LIVEKIT=true
```

---

## 4. 검증

### 서버 상태 확인

```bash
# LiveKit 서버 응답 확인
curl http://GCP_EXTERNAL_IP:7880

# Token Server 테스트
curl -X POST http://GCP_EXTERNAL_IP:8081/api/v1/livekit/token \
  -H "Authorization: Bearer dev" \
  -H "Content-Type: application/json" \
  -d '{"room_name":"test-room","participant_identity":"user1","participant_name":"Test User"}'
```

### 포트 연결 테스트

```bash
# 외부에서 테스트
nc -zv GCP_EXTERNAL_IP 7880
nc -zv GCP_EXTERNAL_IP 8081
```

---

## 5. 로컬 vs 프로덕션 설정 비교

| 항목 | 로컬 | 프로덕션 |
|------|------|----------|
| `LIVEKIT_URL` | `ws://192.168.x.x:7880` | `wss://domain.com` 또는 `ws://EXTERNAL_IP:7880` |
| `use_external_ip` | `false` | `true` |
| `LIVEKIT_API_KEY` | `devkey` | 보안 키 (32자 이상 권장) |
| `LIVEKIT_API_SECRET` | `secret1234567890` | 보안 시크릿 (32자 이상 권장) |
| 방화벽 | 없음 | UDP 50000-50100 개방 필수 |
| SSL | 불필요 | 권장 (wss://) |

---

## 6. 트러블슈팅

### WebRTC 연결 실패
- `use_external_ip: true` 설정 확인
- UDP 포트 (50000-50100) 방화벽 개방 확인
- TURN 서버 활성화 고려

### Token 발급 실패
- `LIVEKIT_API_KEY`, `LIVEKIT_API_SECRET` 일치 확인
- Token Server 로그 확인: `docker compose logs token-server`

### Voice Agent 연결 실패
- `agent.enabled: true` 설정 확인
- Voice Agent 로그 확인: `docker compose logs voice-agent`

### SSE 서버 연결 실패
- `SSE_SERVER_URL`, `SSE_AUTH_TOKEN` 확인
- 네트워크 연결 테스트: `curl -v http://210.109.53.87:8083/health`

---

## 7. 백업 및 복구

### 환경변수 백업
```bash
cp .env .env.backup
```

### Docker 볼륨 백업
```bash
docker run --rm -v livekit-voice-server_redis-data:/data -v $(pwd):/backup alpine tar cvf /backup/redis-backup.tar /data
```

### 복구
```bash
docker run --rm -v livekit-voice-server_redis-data:/data -v $(pwd):/backup alpine tar xvf /backup/redis-backup.tar -C /
```
