# Pwooda - Life Help Agent App for Developmental Disabilities, Mental Retardation

## 📱 Project Overview

Pwooda is an AI-based Android application designed to help people with developmental disabilities in their daily lives. It provides personalized motivation, schedule guidance, behavior improvement tips, and more based on individual data and welfare facility programs. Through voice conversations and AI image generation, it offers rich interactions for users.

## ✨ Key Features

### 🤖 AI-Based Personalized Services
- **Personalized Content Based on Individual Data**: Customized services based on user interests, schedules, goals, etc.
- **11 Service Categories**: Schedule, motivation, medication guidance, life skills, social skills, safety, behavior improvement, object explanation, general conversation, image generation, image saving
- **Conversation History-Based Intent Analysis**: Accurate intent understanding considering previous conversation context
- **Continuous Conversations**: Remembers the last 10 user questions and 3 AI responses for natural conversation flow

### 🎤 Voice Interaction
- **Speech Recognition**: Converts user voice input to text
- **TTS (Text-to-Speech)**: Outputs AI responses in natural voice
- **Teenage Girl Tone**: Communicates with users in a friendly and cute tone

### 👤 User Recognition
- **Face Recognition-Based User Identification**: Detects user faces through camera to provide personalized services
- **User Registration**: Asks for name during first recognition and registers
- **Personal Data Mapping**: Links with registered user information

### 📷 Camera Features
- **Object Recognition**: Provides descriptions of objects photographed with camera
- **Schedule Relevance Analysis**: Guides the relationship between photographed objects and user's schedule/programs

### 🎨 AI Image Generation
- **ComfyUI-Based Local Image Generation**: Fast and secure local image generation
- **Gemini-Based Korean-English Prompt Translation**: Converts user's Korean requests to accurate English prompts
- **Background-Free Ghibli Style**: Creates clean and beautiful images with transparent backgrounds and Ghibli style
- **Real-Time Image Generation**: High-quality image generation within 15-20 seconds

### 💾 Image Saving Feature
- **Gallery Storage**: Saves generated images to Android's default photo album
- **Automatic Folder Creation**: Automatically saves to `Pictures/Pwooda` folder
- **Save Intent Recognition**: Automatically recognizes save requests like "save the picture", "save to album"

## 🏗️ Technology Stack

### Android App
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Networking**: OkHttp
- **JSON Processing**: JSONObject/JSONArray
- **Image Processing**: Bitmap, Base64 encoding/decoding
- **Gallery Storage**: MediaStore API
- **Permission Management**: Context-based gallery access

### AI Services
- **Natural Language Processing**: Google Gemini API
- **Speech Synthesis**: Google Cloud TTS
- **Face Recognition**: ML Kit Face Detection
- **Image Generation**: ComfyUI (local server)

### ComfyUI Server
- **Python**: 3.10.18
- **PyTorch**: 2.7.1
- **ComfyUI**: 0.3.45
- **Hardware Acceleration**: MPS (Metal Performance Shaders)
- **Model**: SDXL (Stable Diffusion XL)
- **Image Size**: 512x512 (optimized)
- **Sampling Steps**: 20 (for Ghibli style)

### Network and Security
- **HTTP Communication**: Local ComfyUI server access
- **Network Security Config**: HTTP communication allowance settings
- **IP Address**: `192.168.219.122:8000` (Mac M4 local server)
- **Timeout**: 120 seconds (for image generation)

### Permission Management
- **WRITE_EXTERNAL_STORAGE**: Gallery write for Android 9 and below
- **READ_EXTERNAL_STORAGE**: Gallery read for Android 13 and below
- **MediaStore API**: Android 10+ Scoped Storage support

## 📊 Service Categories

| No. | Category | Description | Example |
|-----|----------|-------------|---------|
| 1 | Schedule | Today's schedule, schedule, program guidance | "What's today's plan?", "Tell me the schedule" |
| 2 | Goals/Motivation | Goals, motivation, encouragement, praise | "What's your goal?", "Motivate me" |
| 3 | Medication Guidance | Medication intake, side effects, emergency situations | "When should I take medicine?", "What are the side effects?" |
| 4 | Life Skills | Daily living skills, cooking, cleaning, personal hygiene | "How do I wash hands?", "How do I brush teeth?" |
| 5 | Social Skills | Conversation, greetings, friendships, social situations | "How do I greet?", "How do I talk with friends?" |
| 6 | Safety | Safety, protection, dangerous situations, first aid | "When should I call 119?", "There's a fire" |
| 7 | Behavior Improvement | Behavior improvement, emotional expression, anxiety relief | "I'm angry", "I'm anxious" |
| 8 | Object Explanation | Requests for explanation of objects, items, photos | "What's this?", "Explain this" |
| 9 | Drawing | Drawing requests, image generation requests | "Draw a picture", "Create an image" |
| 10 | Save Drawing | Requests to save generated drawings | "Save the picture", "Save to album" |
| 11 | General Conversation | All other questions | "Hello", "I'm feeling good" |

## 🚀 Usage

### 1. App Launch and Permission Grant
```bash
# When launching the app, the following permissions are requested
- Camera permission
- Microphone permission
- Gallery storage permission (Android 9 and below)
```

### 2. First-Time User Registration
```
1. When a face is detected by camera, voice guidance
   "Hello! What's your name? Please say it clearly!"

2. When name is spoken clearly, user registration completes
   - If the name is not registered, guidance is repeated

3. After registration, personalized services begin
```

### 3. Voice Conversation
```
User: "Tell me today's schedule"
AI: "Nuri! Today you have morning exercise at 9 AM, art activity at 10 AM~ 😊"

User: "When is that?"
AI: "Remember what I said earlier? Morning exercise at 9 AM, art activity at 10 AM!"
```

### 4. AI Image Generation
```
User: "Draw a cute puppy"
AI: "I'll draw a picture for you! Please wait a moment."
→ Gemini converts Korean request to English prompt
→ ComfyUI generates image with Ghibli style + transparent background
→ Completed image displayed after 15-20 seconds
```

### 5. Image Saving
```
User: "Save the picture"
AI: "I've saved the picture to your gallery!"
→ Automatically saved to Pictures/Pwooda folder
→ Filename: Pwooda_timestamp.jpg
```

### 6. Camera Object Recognition
```
1. Click camera button
2. Point at object for automatic recognition
3. Object description + guidance on relevance to today's schedule
```

## 🔧 Installation and Setup

### ComfyUI Server Setup

#### 1. ComfyUI Installation and Configuration
```bash
# Navigate to ComfyUI directory
cd /Users/tony/ComfyUI

# Activate virtual environment
source venv/bin/activate

# Run server with MPS optimization
PYTORCH_ENABLE_MPS_FALLBACK=1 python main.py --listen 0.0.0.0 --port 8000 --use-split-cross-attention
```

#### 2. Server Status Check
```bash
# Check server connection
curl http://192.168.219.122:8000

# Check logs
# - Verify MPS acceleration activation
# - Verify SDXL model loading
# - Verify successful image generation logs
```

### Android App Build and Installation

#### 1. Project Build
```bash
# Navigate to project directory
cd /Users/tony/AndroidProjects/pwooda

# Gradle build
./gradlew assembleDebug
```

#### 2. APK Installation
```bash
# Install APK on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Verify installation
adb shell pm list packages | grep pwooda
```

## 📁 Project Structure

```
pwooda/
├── app/
│   ├── src/main/
│   │   ├── java/com/banya/pwooda/
│   │   │   ├── data/                    # Data models
│   │   │   │   ├── CustomerData.kt
│   │   │   │   └── ProductData.kt
│   │   │   ├── service/                 # Service classes
│   │   │   │   ├── FalAIService.kt      # ComfyUI image generation
│   │   │   │   ├── GoogleCloudTTSService.kt
│   │   │   │   ├── CustomerDataService.kt
│   │   │   │   └── PaymentService.kt
│   │   │   ├── viewmodel/
│   │   │   │   └── GeminiViewModel.kt   # Main ViewModel
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
│   │   │   ├── data.json               # User data
│   │   │   └── google_tts_key.json
│   │   └── res/
│   │       ├── xml/
│   │       │   └── network_security_config.xml
│   │       └── values/
│   └── build.gradle.kts
├── build.gradle.kts
└── README.md
```

## 🎯 Core Features Detail

### Conversation History-Based Intent Analysis
```kotlin
// Include recent 6 conversation messages in intent analysis
val recentHistory = chatHistory.takeLast(6)
val historyContext = if (recentHistory.isNotEmpty()) {
    "Previous conversation:\n" + recentHistory.joinToString("\n") { "${it.role}: ${it.content}" }
} else {
    "No previous conversation"
}
```

### Gemini-Based Prompt Translation
```kotlin
// Convert Korean request to English image generation prompt
val translationPrompt = """
    The user has requested to draw a picture. Please convert the following request to an English image generation prompt.
    
    Requirements:
    1. Translate the user's request to English and convert it to keywords suitable for image generation
    2. Add "transparent background, no background, isolated" to generate images without background
    3. Add "Studio Ghibli style, Hayao Miyazaki, anime, watercolor, soft lighting, magical atmosphere" to generate in Ghibli style
    4. Add "detailed, high quality, masterpiece" to generate high-quality images
"""
```

### ComfyUI Workflow Structure
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

## 🔄 Recent Updates (December 2024)

### v2.0 - Conversation History-Based Intent Analysis
- **Conversation History Integration**: Includes recent 6 conversation messages in intent analysis
- **Context-Based Intent Understanding**: Accurate intent analysis considering previous conversations
- **Continuous Conversations**: Accurate recognition of context-dependent questions like "When is that?", "Draw it again"

### v1.9 - Image Saving Feature
- **Gallery Storage**: Saves generated images to Android's default photo album
- **MediaStore API**: Android 10+ Scoped Storage support
- **Save Intent Recognition**: Automatic recognition of "save the picture", "save to album"
- **Error Handling**: Guidance message when no image to save

### v1.8 - Conversation History Management Improvement
- **Drawing Conversation Recording**: Drawing and save requests properly recorded in conversation history
- **Image Clearing**: Previous images automatically deleted when starting new conversation
- **Continuity Assurance**: All interactions recorded in conversation history to maintain context

### v1.7 - Gemini-Based Korean-English Prompt Translation
- **Korean Request Support**: Directly receives and processes user's Korean requests
- **Gemini Translation**: Converts Korean requests to accurate English prompts
- **SDXL Limitation Resolution**: Solves SDXL model's Korean understanding limitation
- **More Accurate Images**: Generates accurate images matching user intent

### v1.6 - Background-Free Ghibli Style Images
- **Transparent Background**: Generates clean images without background
- **Ghibli Style**: Automatically applies Studio Ghibli style prompts
- **High-Quality Optimization**: Adds detailed, high quality, masterpiece keywords
- **Consistent Style**: All images generated in same art style

### v1.5 - ComfyUI-Based Image Generation
- **Local Image Generation**: Fast and secure image generation through ComfyUI server
- **SDXL Model**: Uses high-quality Stable Diffusion XL model
- **MPS Acceleration**: Performance optimization with Apple Silicon GPU acceleration
- **512x512 Optimization**: Balance between fast generation and quality

### v1.4 - Network Security Configuration
- **HTTP Communication Allowance**: Network Security Config for local ComfyUI server access
- **IP Address Setting**: Allows access to Mac M4 local server (192.168.219.122:8000)
- **AndroidManifest.xml**: Security settings applied

### v1.3 - Performance Optimization
- **Timeout Extension**: Extended image generation timeout to 120 seconds
- **Sampling Optimization**: Optimized for Ghibli style with 20 steps
- **MPS Acceleration**: Apple Silicon GPU utilization optimization
- **Memory Management**: Efficient VRAM usage

## 🐛 Known Issues

### ComfyUI Server Related
- **Bad linked input Error**: Intermittently occurs due to workflow JSON structure issues
- **JSON Decode Error**: JSON parsing error during network transmission
- **Solution**: Server restart or workflow retransmission

### Android App Related
- **Permission Requests**: Automatic handling of gallery storage permissions on Android 10+
- **Network Timeout**: Timeout occurs when image generation takes too long
- **Solution**: Most issues resolved with 120-second timeout setting

## 🤝 Contributing and Inquiries

### Bug Reports
- Please report bugs through GitHub Issues
- Include detailed reproduction methods with logs

### Feature Suggestions
- Suggest new feature ideas through Pull Requests
- Suggestions that improve user experience for people with developmental disabilities are welcome

### Development Environment Setup
- Android Studio Arctic Fox or higher
- Kotlin 1.8+
- Java 17
- ComfyUI Python 3.10+

## 📄 License

This project is distributed under the MIT License.

## 🙏 Acknowledgments

- **Google Gemini API**: Natural language processing and prompt translation
- **ComfyUI**: Local image generation server
- **Stability AI**: SDXL model provision
- **Apple**: MPS acceleration support

---

**Pwooda** - AI friend for people with developmental disabilities 🫂✨

## 🆕 July 2025 Major Updates and UX Improvements

- **Splash Music**: intro_music.mp3 plays when app launches, main screen only loads after music ends (skip button supported)
- **Gemini-Based Name Recognition**: When recognizing names, Gemini LLM generates natural friendly welcome messages and outputs directly to TTS/screen
- **Image Generation Animation**: During image generation, running.mp4 (or sprite sheet) based animation plays in screen center with transparent background
- **Sprite Sheet Animation**: Utilizes drawing_sheet.png (5x9, 43 frames) sprite sheet to provide smooth animation during image generation (transparent background supported)
- **Completed Image Round Masking**: AI-generated completed images are masked in rounded corner boxes (32dp) for clean display
- **Duplicate Image Output Prevention**: UI improvement to display completed images only once
- **Consistent Friendly Tone**: All conversations including name recognition, welcome messages, and guidance output in consistent 10-year-old girl friend tone

## 🖼️ Image Generation UX Example

1. When user says "Draw a cute puppy"
2. Sprite sheet animation (transparent background) plays in center with "Image generating..." text displayed
3. When image generation completes, completed image is displayed once in rounded corner box
4. When "save the picture" is requested, it's saved to gallery

## 👤 Name Recognition/Registration UX Example

1. After face recognition, "What's your name?" voice guidance
2. When user says name, Gemini extracts name and outputs friendly welcome message like "Tony! Nice to meet you~ I'll call you often from now on!" to screen+TTS
3. If name is not registered, guidance is repeated
