FROM python:3.11-slim

WORKDIR /app

# Install system dependencies (including GLib for livekit-rtc)
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libglib2.0-0 \
    libgobject-2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application
COPY *.py .

# Run the agent
CMD ["python", "main.py", "start"]
