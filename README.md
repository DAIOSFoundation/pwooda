# 피우다 (Pwooda) - 발달장애인 도우미 앱

## 📱 프로젝트 개요

피우다는 발달장애인의 일상생활을 돕기 위한 AI 기반 안드로이드 애플리케이션입니다. 개인별 맞춤형 데이터와 복지기관 프로그램을 기반으로 동기부여, 일정 안내, 행동 개선 팁 등을 제공하며, 음성 대화와 AI 이미지 생성 기능을 통해 더욱 풍부한 상호작용을 지원합니다.

## ✨ 주요 기능

### 🤖 AI 기반 맞춤형 서비스
- **개인별 데이터 기반 맞춤형 컨텐츠**: 사용자의 관심사, 일정, 목표 등을 기반으로 한 맞춤형 서비스
- **11가지 서비스 카테고리**: 일정, 동기부여, 약물 안내, 생활기술, 사회성, 안전, 행동개선, 사물설명, 일반대화, 이미지 생성, 이미지 저장
- **대화 히스토리 기반 의도 분석**: 이전 대화 맥락을 고려한 정확한 의도 파악
- **연속성 있는 대화**: 최근 10개 사용자 질문과 3개 AI 응답을 기억하여 자연스러운 대화 진행

### 🎤 음성 상호작용
- **음성 인식**: 사용자의 음성 입력을 텍스트로 변환
- **TTS (Text-to-Speech)**: AI 응답을 자연스러운 음성으로 출력
- **10대 소녀 말투**: 친근하고 귀여운 말투로 사용자와 소통

### 👤 사용자 인식
- **얼굴 인식 기반 사용자 식별**: 카메라로 사용자 얼굴을 감지하여 개인별 맞춤 서비스 제공
- **사용자 등록**: 최초 인식 시 이름을 물어보고 등록
- **개인별 데이터 매핑**: 등록된 사용자 정보와 연동

### 📷 카메라 기능
- **사물 인식**: 카메라로 촬영한 사물에 대한 설명 제공
- **일정 연관성 분석**: 촬영한 사물과 사용자의 일정/프로그램과의 연관성 안내

### 🎨 AI 이미지 생성
- **ComfyUI 기반 로컬 이미지 생성**: 빠르고 안전한 로컬 이미지 생성
- **Gemini 기반 한글-영문 프롬프트 변환**: 사용자의 한글 요청을 정확한 영문 프롬프트로 변환
- **배경 없는 지브리 스타일**: 투명 배경과 지브리 스타일로 깔끔하고 아름다운 이미지 생성
- **실시간 이미지 생성**: 15-20초 내 고품질 이미지 생성

### 💾 이미지 저장 기능
- **갤러리 저장**: 생성된 이미지를 안드로이드 기본 사진 앨범에 저장
- **자동 폴더 생성**: `Pictures/Pwooda` 폴더에 자동 저장
- **저장 의도 인식**: "그림 저장해줘", "앨범에 저장해줘" 등 저장 요청 자동 인식

## 🏗️ 기술 스택

### Android 앱
- **언어**: Kotlin
- **UI 프레임워크**: Jetpack Compose
- **아키텍처**: MVVM (Model-View-ViewModel)
- **네트워킹**: OkHttp
- **JSON 처리**: JSONObject/JSONArray
- **이미지 처리**: Bitmap, Base64 인코딩/디코딩
- **갤러리 저장**: MediaStore API
- **권한 관리**: Context 기반 갤러리 접근

### AI 서비스
- **자연어 처리**: Google Gemini API
- **음성 합성**: Google Cloud TTS
- **얼굴 인식**: ML Kit Face Detection
- **이미지 생성**: ComfyUI (로컬 서버)

### ComfyUI 서버
- **Python**: 3.10.18
- **PyTorch**: 2.7.1
- **ComfyUI**: 0.3.45
- **하드웨어 가속**: MPS (Metal Performance Shaders)
- **모델**: SDXL (Stable Diffusion XL)
- **이미지 크기**: 512x512 (최적화)
- **샘플링 스텝**: 20 (지브리 스타일용)

### 네트워크 및 보안
- **HTTP 통신**: 로컬 ComfyUI 서버 접근
- **Network Security Config**: HTTP 통신 허용 설정
- **IP 주소**: `192.168.219.122:8000` (Mac M4 로컬 서버)
- **타임아웃**: 120초 (이미지 생성용)

### 권한 관리
- **WRITE_EXTERNAL_STORAGE**: Android 9 이하 갤러리 쓰기
- **READ_EXTERNAL_STORAGE**: Android 13 이하 갤러리 읽기
- **MediaStore API**: Android 10+ Scoped Storage 지원

## 📊 서비스 카테고리

| 번호 | 카테고리 | 설명 | 예시 |
|------|----------|------|------|
| 1 | 일정 | 오늘 일정, 스케줄, 프로그램 안내 | "오늘 뭐해?", "일정 알려줘" |
| 2 | 목표/동기부여 | 목표, 동기부여, 응원, 칭찬 | "목표가 뭐야?", "동기부여해줘" |
| 3 | 약물 안내 | 약물 복용, 부작용, 응급상황 | "약 언제 먹어?", "약 부작용 뭐야?" |
| 4 | 생활기술 | 일상생활 기술, 요리, 청소, 개인위생 | "손 씻는 법 알려줘", "양치질 어떻게 해?" |
| 5 | 사회성 | 대화, 인사, 친구관계, 사회적 상황 | "인사 어떻게 해?", "친구랑 어떻게 대화해?" |
| 6 | 안전 | 안전, 보호, 위험상황, 응급처치 | "119 언제 불러?", "화재 났어" |
| 7 | 행동개선 | 행동 개선, 감정 표현, 불안 해소 | "화가 나", "불안해" |
| 8 | 사물설명 | 사물, 물건, 사진에 대한 설명 요청 | "이게 뭐야?", "이거 설명해줘" |
| 9 | 그림그리기 | 그림 그리기, 이미지 생성 요청 | "그림 그려줘", "이미지 만들어줘" |
| 10 | 그림저장 | 생성된 그림을 저장하는 요청 | "그림 저장해줘", "앨범에 저장해줘" |
| 11 | 일반대화 | 기타 모든 질문들 | "안녕", "기분이 좋아" |

## 🚀 사용법

### 1. 앱 실행 및 권한 허용
```bash
# 앱을 실행하면 다음 권한을 요청합니다
- 카메라 권한
- 마이크 권한
- 갤러리 저장 권한 (Android 9 이하)
```

### 2. 최초 사용자 등록
```
1. 카메라에 얼굴이 감지되면 음성 안내
   "안녕! 혹시 네 이름이 뭐야? 예쁘게 말해줘!"

2. 이름을 또박또박 말하면 사용자 등록 완료
   - 등록된 이름이 아닐 경우 다시 안내

3. 등록 완료 후 개인별 맞춤 서비스 시작
```

### 3. 음성 대화
```
사용자: "오늘 일정 알려줘"
AI: "누리야! 오늘은 9시 아침 운동, 10시 미술 활동이야~ 😊"

사용자: "그거 언제야?"
AI: "아까 말한 일정 기억해? 9시에 아침 운동, 10시에 미술 활동이야!"
```

### 4. AI 이미지 생성
```
사용자: "귀여운 강아지 그림 그려줘"
AI: "그림을 그려줄게! 잠깐만 기다려."
→ Gemini가 한글 요청을 영문 프롬프트로 변환
→ ComfyUI에서 지브리 스타일 + 투명 배경으로 이미지 생성
→ 15-20초 후 완성된 이미지 표시
```

### 5. 이미지 저장
```
사용자: "그림 저장해줘"
AI: "그림을 갤러리에 저장했어요!"
→ Pictures/Pwooda 폴더에 자동 저장
→ 파일명: Pwooda_타임스탬프.jpg
```

### 6. 카메라 사물 인식
```
1. 카메라 버튼 클릭
2. 사물을 비추면 자동 인식
3. 사물 설명 + 오늘 일정과의 연관성 안내
```

## 🔧 설치 및 실행

### ComfyUI 서버 설정

#### 1. ComfyUI 설치 및 설정
```bash
# ComfyUI 디렉토리로 이동
cd /Users/tony/ComfyUI

# 가상환경 활성화
source venv/bin/activate

# MPS 최적화와 함께 서버 실행
PYTORCH_ENABLE_MPS_FALLBACK=1 python main.py --listen 0.0.0.0 --port 8000 --use-split-cross-attention
```

#### 2. 서버 상태 확인
```bash
# 서버 접속 확인
curl http://192.168.219.122:8000

# 로그 확인
# - MPS 가속 활성화 확인
# - SDXL 모델 로드 확인
# - 이미지 생성 성공 로그 확인
```

### Android 앱 빌드 및 설치

#### 1. 프로젝트 빌드
```bash
# 프로젝트 디렉토리로 이동
cd /Users/tony/AndroidProjects/pwooda

# Gradle 빌드
./gradlew assembleDebug
```

#### 2. APK 설치
```bash
# 디바이스에 APK 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 설치 확인
adb shell pm list packages | grep pwooda
```

## 📁 프로젝트 구조

```
pwooda/
├── app/
│   ├── src/main/
│   │   ├── java/com/banya/pwooda/
│   │   │   ├── data/                    # 데이터 모델
│   │   │   │   ├── CustomerData.kt
│   │   │   │   └── ProductData.kt
│   │   │   ├── service/                 # 서비스 클래스
│   │   │   │   ├── FalAIService.kt      # ComfyUI 이미지 생성
│   │   │   │   ├── GoogleCloudTTSService.kt
│   │   │   │   ├── CustomerDataService.kt
│   │   │   │   └── PaymentService.kt
│   │   │   ├── viewmodel/
│   │   │   │   └── GeminiViewModel.kt   # 메인 ViewModel
│   │   │   ├── ui/
│   │   │   │   ├── screens/
│   │   │   │   │   └── MainScreen.kt
│   │   │   │   ├── components/
│   │   │   │   │   ├── CameraComponent.kt
│   │   │   │   │   └── SpeechRecognitionComponent.kt
│   │   │   │   └── theme/
│   │   │   ├── MainActivity.kt
│   │   │   └── SplashActivity.kt
│   │   ├── assets/
│   │   │   ├── data.json               # 사용자 데이터
│   │   │   └── google_tts_key.json
│   │   └── res/
│   │       ├── xml/
│   │       │   └── network_security_config.xml
│   │       └── values/
│   └── build.gradle.kts
├── build.gradle.kts
└── README.md
```

## 🎯 핵심 기능 상세

### 대화 히스토리 기반 의도 분석
```kotlin
// 최근 6개 대화 메시지를 의도 분석에 포함
val recentHistory = chatHistory.takeLast(6)
val historyContext = if (recentHistory.isNotEmpty()) {
    "이전 대화:\n" + recentHistory.joinToString("\n") { "${it.role}: ${it.content}" }
} else {
    "이전 대화 없음"
}
```

### Gemini 기반 프롬프트 변환
```kotlin
// 한글 요청을 영문 이미지 생성 프롬프트로 변환
val translationPrompt = """
    사용자가 그림 그리기를 요청했습니다. 다음 요청을 영문 이미지 생성 프롬프트로 변환해줘.
    
    요구사항:
    1. 사용자의 요청을 영어로 번역하고, 이미지 생성에 적합한 키워드로 변환
    2. 배경 없는 이미지로 생성되도록 "transparent background, no background, isolated" 추가
    3. 지브리 스타일로 생성되도록 "Studio Ghibli style, Hayao Miyazaki, anime, watercolor, soft lighting, magical atmosphere" 추가
    4. 고품질 이미지로 생성되도록 "detailed, high quality, masterpiece" 추가
"""
```

### ComfyUI 워크플로우 구조
```json
{
  "1": {"class_type": "CheckpointLoaderSimple", "inputs": {"ckpt_name": "sd_xl_base_1.0.safetensors"}},
  "2": {"class_type": "CLIPTextEncode", "inputs": {"text": "prompt", "clip": ["1", 1]}},
  "3": {"class_type": "CLIPTextEncode", "inputs": {"text": "negative_prompt", "clip": ["1", 1]}},
  "4": {"class_type": "EmptyLatentImage", "inputs": {"width": 512, "height": 512, "batch_size": 1}},
  "5": {"class_type": "KSampler", "inputs": {"seed": 123, "steps": 20, "cfg": 7.0, "sampler_name": "dpmpp_2m", "scheduler": "karras", "denoise": 1.0, "model": ["1", 0], "positive": ["2", 0], "negative": ["3", 0], "latent_image": ["4", 0]}},
  "6": {"class_type": "VAEDecode", "inputs": {"samples": ["5", 0], "vae": ["1", 2]}},
  "7": {"class_type": "SaveImage", "inputs": {"images": ["6", 0], "filename_prefix": "ComfyUI"}}
}
```

## 🔄 최근 업데이트 (2024년 12월)

### v2.0 - 대화 히스토리 기반 의도 분석
- **대화 히스토리 통합**: 최근 6개 대화 메시지를 의도 분석에 포함
- **맥락 기반 의도 파악**: 이전 대화를 고려한 정확한 의도 분석
- **연속성 있는 대화**: "그거 언제야?", "다시 그려줘" 등 맥락 의존적 질문 정확 인식

### v1.9 - 이미지 저장 기능
- **갤러리 저장**: 생성된 이미지를 안드로이드 기본 사진 앨범에 저장
- **MediaStore API**: Android 10+ Scoped Storage 지원
- **저장 의도 인식**: "그림 저장해줘", "앨범에 저장해줘" 등 자동 인식
- **오류 처리**: 저장할 이미지가 없을 경우 안내 메시지

### v1.8 - 대화 히스토리 관리 개선
- **그림 그리기 대화 기록**: 그림 그리기와 저장 요청이 대화 히스토리에 정상 기록
- **이미지 클리어링**: 새 대화 시작 시 이전 이미지 자동 삭제
- **연속성 보장**: 모든 상호작용이 대화 히스토리에 기록되어 맥락 유지

### v1.7 - Gemini 기반 한글-영문 프롬프트 변환
- **한글 요청 지원**: 사용자의 한글 요청을 직접 받아 처리
- **Gemini 번역**: 한글 요청을 정확한 영문 프롬프트로 변환
- **SDXL 한계 해결**: SDXL 모델의 한글 이해 한계 문제 해결
- **더 정확한 이미지**: 의도에 맞는 정확한 이미지 생성

### v1.6 - 배경 없는 지브리 스타일 이미지
- **투명 배경**: 배경 없는 깔끔한 이미지 생성
- **지브리 스타일**: Studio Ghibli 스타일 프롬프트 자동 적용
- **고품질 최적화**: detailed, high quality, masterpiece 키워드 추가
- **일관된 스타일**: 모든 이미지가 동일한 아트 스타일로 생성

### v1.5 - ComfyUI 기반 이미지 생성
- **로컬 이미지 생성**: ComfyUI 서버를 통한 빠르고 안전한 이미지 생성
- **SDXL 모델**: 고품질 Stable Diffusion XL 모델 사용
- **MPS 가속**: Apple Silicon GPU 가속으로 성능 최적화
- **512x512 최적화**: 빠른 생성과 품질의 균형

### v1.4 - 네트워크 보안 설정
- **HTTP 통신 허용**: 로컬 ComfyUI 서버 접근을 위한 Network Security Config
- **IP 주소 설정**: Mac M4 로컬 서버 (192.168.219.122:8000) 접근 허용
- **AndroidManifest.xml**: 보안 설정 적용

### v1.3 - 성능 최적화
- **타임아웃 연장**: 이미지 생성 타임아웃 120초로 연장
- **샘플링 최적화**: 20 스텝으로 지브리 스타일 최적화
- **MPS 가속**: Apple Silicon GPU 활용 최적화
- **메모리 관리**: 효율적인 VRAM 사용

## 🐛 알려진 이슈

### ComfyUI 서버 관련
- **Bad linked input 오류**: 워크플로우 JSON 구조 문제로 간헐적 발생
- **JSON Decode 오류**: 네트워크 전송 중 JSON 파싱 오류
- **해결책**: 서버 재시작 또는 워크플로우 재전송

### Android 앱 관련
- **권한 요청**: Android 10+ 에서 갤러리 저장 권한 자동 처리
- **네트워크 타임아웃**: 이미지 생성 시간이 길 경우 타임아웃 발생
- **해결책**: 120초 타임아웃 설정으로 대부분 해결

## 🤝 기여 및 문의

### 버그 리포트
- GitHub Issues를 통해 버그를 리포트해주세요
- 로그와 함께 상세한 재현 방법을 포함해주세요

### 기능 제안
- 새로운 기능 아이디어는 Pull Request로 제안해주세요
- 발달장애인 사용자 경험 개선에 도움이 되는 제안을 환영합니다

### 개발 환경 설정
- Android Studio Arctic Fox 이상
- Kotlin 1.8+
- Java 17
- ComfyUI Python 3.10+

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.

## 🙏 감사의 말

- **Google Gemini API**: 자연어 처리 및 프롬프트 변환
- **ComfyUI**: 로컬 이미지 생성 서버
- **Stability AI**: SDXL 모델 제공
- **Apple**: MPS 가속 지원

---

**피우다** - 발달장애인과 함께하는 AI 친구 🫂✨
