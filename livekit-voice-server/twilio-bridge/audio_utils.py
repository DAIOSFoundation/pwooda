"""
Audio utility functions for Twilio Media Streams.
Handles conversion between mulaw 8kHz (Twilio) and PCM 24kHz (SSE server TTS).
"""

import audioop
import struct
from typing import Optional

import numpy as np


def mulaw_to_pcm16(mulaw_bytes: bytes) -> bytes:
    """Convert mulaw encoded audio to 16-bit PCM."""
    return audioop.ulaw2lin(mulaw_bytes, 2)


def pcm16_to_mulaw(pcm_bytes: bytes) -> bytes:
    """Convert 16-bit PCM audio to mulaw encoding."""
    return audioop.lin2ulaw(pcm_bytes, 2)


def resample(pcm_bytes: bytes, from_rate: int, to_rate: int) -> bytes:
    """Resample PCM 16-bit audio from one sample rate to another."""
    if from_rate == to_rate:
        return pcm_bytes

    # Convert bytes to numpy array (16-bit signed)
    samples = np.frombuffer(pcm_bytes, dtype=np.int16).astype(np.float32)

    # Calculate resampling ratio
    ratio = to_rate / from_rate
    new_length = int(len(samples) * ratio)

    if new_length == 0:
        return b""

    # Linear interpolation resampling
    indices = np.linspace(0, len(samples) - 1, new_length)
    resampled = np.interp(indices, np.arange(len(samples)), samples)

    return resampled.astype(np.int16).tobytes()


def mulaw_8k_to_pcm_16k(mulaw_bytes: bytes) -> bytes:
    """Convert Twilio mulaw 8kHz to PCM 16-bit 16kHz (for Whisper STT)."""
    # mulaw -> PCM 16-bit at 8kHz
    pcm_8k = mulaw_to_pcm16(mulaw_bytes)
    # Resample 8kHz -> 16kHz
    pcm_16k = resample(pcm_8k, 8000, 16000)
    return pcm_16k


def pcm_24k_to_mulaw_8k(pcm_bytes: bytes) -> bytes:
    """Convert SSE server TTS audio (PCM 24kHz) to mulaw 8kHz (for Twilio)."""
    # Resample 24kHz -> 8kHz
    pcm_8k = resample(pcm_bytes, 24000, 8000)
    # PCM -> mulaw
    mulaw_bytes = pcm16_to_mulaw(pcm_8k)
    return mulaw_bytes


def pcm_to_float32(pcm_bytes: bytes) -> np.ndarray:
    """Convert 16-bit PCM bytes to float32 numpy array (range -1.0 to 1.0)."""
    samples = np.frombuffer(pcm_bytes, dtype=np.int16)
    return samples.astype(np.float32) / 32768.0


def chunk_audio(audio_bytes: bytes, chunk_size: int = 640) -> list[bytes]:
    """Split audio bytes into chunks of specified size.

    Default chunk_size=640 bytes = 20ms of mulaw 8kHz (8000 samples/sec * 0.02 sec * 1 byte/sample)
    """
    chunks = []
    for i in range(0, len(audio_bytes), chunk_size):
        chunk = audio_bytes[i:i + chunk_size]
        if len(chunk) == chunk_size:
            chunks.append(chunk)
        elif len(chunk) > 0:
            # Pad the last chunk with silence (mulaw silence = 0xFF)
            chunk = chunk + b'\xff' * (chunk_size - len(chunk))
            chunks.append(chunk)
    return chunks
