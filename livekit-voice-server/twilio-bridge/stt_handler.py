"""
Server-side STT handler for Twilio phone calls.
Uses VAD (Voice Activity Detection) + faster-whisper for Korean speech recognition.
"""

import logging
import struct
import io
import wave
from typing import Optional, Callable

import numpy as np
import webrtcvad
from faster_whisper import WhisperModel

from audio_utils import mulaw_to_pcm16, resample

logger = logging.getLogger(__name__)


class STTHandler:
    """
    Handles real-time speech-to-text for Twilio audio streams.

    Flow:
    1. Receive mulaw 8kHz audio chunks from Twilio
    2. Convert to PCM and run VAD to detect speech segments
    3. When speech ends (silence detected), transcribe with Whisper
    4. Return transcribed text via callback
    """

    def __init__(
        self,
        on_text: Optional[Callable[[str], None]] = None,
        model_size: str = "base",
        language: str = "ko",
        vad_aggressiveness: int = 2,
        silence_threshold_ms: int = 800,
    ):
        """
        Args:
            on_text: Callback when speech is transcribed
            model_size: Whisper model size (tiny, base, small, medium, large)
            language: Language for STT (ko = Korean)
            vad_aggressiveness: VAD sensitivity (0-3, higher = more aggressive)
            silence_threshold_ms: Silence duration to consider speech ended (ms)
        """
        self.on_text = on_text
        self.language = language
        self.silence_threshold_ms = silence_threshold_ms

        # Initialize VAD
        self.vad = webrtcvad.Vad(vad_aggressiveness)

        # Initialize Whisper model
        logger.info(f"Loading Whisper model: {model_size}")
        self.model = WhisperModel(model_size, device="cpu", compute_type="int8")
        logger.info("Whisper model loaded")

        # Audio buffer for accumulating speech
        self._speech_buffer: list[bytes] = []
        self._is_speaking = False
        self._silence_frames = 0

        # VAD requires specific frame sizes: 10ms, 20ms, or 30ms at 8kHz/16kHz/32kHz/48kHz
        # For 8kHz audio, 20ms frame = 160 samples = 320 bytes (16-bit PCM)
        self._frame_duration_ms = 20
        self._sample_rate = 8000
        self._frame_size = int(self._sample_rate * self._frame_duration_ms / 1000) * 2  # * 2 for 16-bit

        # Pending PCM data that hasn't been processed into full frames yet
        self._pending_pcm = b""

        # Silence frames needed to consider speech ended
        self._silence_frames_threshold = int(
            self.silence_threshold_ms / self._frame_duration_ms
        )

    def process_audio(self, mulaw_chunk: bytes) -> Optional[str]:
        """
        Process a chunk of mulaw 8kHz audio from Twilio.

        Returns transcribed text if speech segment is complete, None otherwise.
        """
        # Convert mulaw to PCM 16-bit at 8kHz
        pcm_8k = mulaw_to_pcm16(mulaw_chunk)

        # Add to pending buffer
        self._pending_pcm += pcm_8k

        result = None

        # Process complete frames
        while len(self._pending_pcm) >= self._frame_size:
            frame = self._pending_pcm[:self._frame_size]
            self._pending_pcm = self._pending_pcm[self._frame_size:]

            # Run VAD on the frame
            try:
                is_speech = self.vad.is_speech(frame, self._sample_rate)
            except Exception:
                is_speech = False

            if is_speech:
                if not self._is_speaking:
                    logger.debug("Speech started")
                    self._is_speaking = True
                    self._silence_frames = 0

                self._speech_buffer.append(frame)
                self._silence_frames = 0

            else:
                if self._is_speaking:
                    self._silence_frames += 1
                    # Still append frames during short silences (natural pauses)
                    self._speech_buffer.append(frame)

                    if self._silence_frames >= self._silence_frames_threshold:
                        # Speech segment ended - transcribe
                        logger.debug(
                            f"Speech ended after {self._silence_frames * self._frame_duration_ms}ms silence, "
                            f"buffer: {len(self._speech_buffer)} frames"
                        )
                        result = self._transcribe()
                        self._reset()

        return result

    def _transcribe(self) -> Optional[str]:
        """Transcribe the accumulated speech buffer using Whisper."""
        if not self._speech_buffer:
            return None

        # Combine all speech frames
        pcm_8k = b"".join(self._speech_buffer)

        # Minimum audio length check (at least 0.5 seconds)
        min_samples = int(self._sample_rate * 0.5) * 2  # 0.5s * 8000 Hz * 2 bytes
        if len(pcm_8k) < min_samples:
            logger.debug("Audio too short, skipping transcription")
            return None

        # Resample 8kHz -> 16kHz (Whisper expects 16kHz)
        pcm_16k = resample(pcm_8k, 8000, 16000)

        # Convert to float32 numpy array
        audio_float = np.frombuffer(pcm_16k, dtype=np.int16).astype(np.float32) / 32768.0

        try:
            # Transcribe with Whisper
            segments, info = self.model.transcribe(
                audio_float,
                language=self.language,
                beam_size=5,
                vad_filter=True,
            )

            text = " ".join([segment.text.strip() for segment in segments]).strip()

            if text:
                logger.info(f"STT result: {text}")
                if self.on_text:
                    self.on_text(text)
                return text

        except Exception as e:
            logger.error(f"Whisper transcription error: {e}", exc_info=True)

        return None

    def _reset(self):
        """Reset speech detection state."""
        self._speech_buffer = []
        self._is_speaking = False
        self._silence_frames = 0

    def flush(self) -> Optional[str]:
        """Force transcribe any remaining audio in the buffer."""
        if self._speech_buffer:
            result = self._transcribe()
            self._reset()
            return result
        return None
