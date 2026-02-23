FROM python:3.11-slim

WORKDIR /app

# System dependencies for audio processing and webrtcvad
RUN apt-get update && apt-get install -y \
    build-essential \
    libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Download Whisper model at build time (avoids download at runtime)
RUN python -c "from faster_whisper import WhisperModel; WhisperModel('base', device='cpu', compute_type='int8')"

# Copy application code
COPY . .

EXPOSE 8082

CMD ["python", "main.py"]
