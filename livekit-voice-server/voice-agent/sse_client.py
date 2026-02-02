"""
SSE Client for existing LLM/TTS server
Handles streaming responses from the server
Supports large audio chunks (100KB+)

Uses httpx for better compatibility with LiveKit's event loop.
"""

import asyncio
import json
import logging
from typing import AsyncIterator, Dict, Any, Optional
from queue import Queue
from threading import Thread

import requests

logger = logging.getLogger(__name__)


class SSEClient:
    """
    Client for the existing SSE server (completion-with-tts).

    Response format:
    - {"type": "text", "content": "..."}
    - {"type": "audio", "audio": "base64_pcm_data", "sentenceId": 1}
    - {"type": "done"}
    """

    def __init__(self, base_url: str, auth_token: str):
        self.base_url = base_url
        self.auth_token = auth_token

    def _fetch_sse_sync(self, payload: Dict[str, Any], event_queue: Queue):
        """
        Synchronous SSE fetch running in a separate thread.
        Puts events into the queue for async consumption.
        """
        headers = {
            "Authorization": f"Bearer {self.auth_token}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream"
        }

        try:
            logger.info(f"[Thread] Starting SSE request to {self.base_url}")

            with requests.post(
                self.base_url,
                json=payload,
                headers=headers,
                stream=True,
                timeout=300
            ) as response:

                if response.status_code != 200:
                    logger.error(f"[Thread] SSE request failed: {response.status_code}")
                    event_queue.put({"type": "error", "message": f"Server error: {response.status_code}"})
                    event_queue.put(None)  # Signal end
                    return

                logger.info(f"[Thread] SSE response status: {response.status_code}")

                buffer = ""
                chunk_count = 0

                for chunk in response.iter_content(chunk_size=8192, decode_unicode=True):
                    if chunk:
                        chunk_count += 1
                        buffer += chunk

                        if chunk_count <= 3:
                            logger.info(f"[Thread] Chunk {chunk_count}: {len(chunk)} chars")

                        # Process complete lines
                        while '\n' in buffer:
                            line, buffer = buffer.split('\n', 1)
                            line = line.strip()

                            if not line:
                                continue

                            if line.startswith('data:'):
                                data_str = line[5:].strip()

                                if data_str == "[DONE]":
                                    logger.info("[Thread] Received [DONE] marker")
                                    event_queue.put({"type": "done"})
                                    event_queue.put(None)  # Signal end
                                    return

                                try:
                                    event = json.loads(data_str)
                                    event_queue.put(event)
                                except json.JSONDecodeError:
                                    logger.warning(f"[Thread] Invalid JSON: {data_str[:100]}...")

                # Process remaining buffer
                if buffer.strip():
                    line = buffer.strip()
                    if line.startswith('data:'):
                        data_str = line[5:].strip()
                        if data_str and data_str != "[DONE]":
                            try:
                                event = json.loads(data_str)
                                event_queue.put(event)
                            except json.JSONDecodeError:
                                pass

                logger.info(f"[Thread] SSE stream completed. Total chunks: {chunk_count}")
                event_queue.put(None)  # Signal end

        except requests.RequestException as e:
            logger.error(f"[Thread] HTTP error: {e}")
            event_queue.put({"type": "error", "message": str(e)})
            event_queue.put(None)
        except Exception as e:
            logger.error(f"[Thread] Error: {e}")
            event_queue.put({"type": "error", "message": str(e)})
            event_queue.put(None)

    async def stream(self, payload: Dict[str, Any]) -> AsyncIterator[Dict[str, Any]]:
        """
        Send a request to the SSE server and stream the response.

        Uses a separate thread for HTTP to avoid event loop conflicts.

        Args:
            payload: Request payload (prompt, tts settings, etc.)

        Yields:
            Parsed SSE events as dictionaries
        """
        event_queue: Queue = Queue()

        # Start the SSE fetch in a separate thread
        thread = Thread(target=self._fetch_sse_sync, args=(payload, event_queue))
        thread.daemon = True
        thread.start()

        logger.info("SSE thread started, waiting for events...")

        # Yield events from the queue
        while True:
            # Check queue with a small delay to not block the event loop
            try:
                # Use asyncio.to_thread for non-blocking queue check
                event = await asyncio.to_thread(event_queue.get, timeout=1.0)

                if event is None:
                    # End of stream
                    logger.info("SSE stream ended (received None)")
                    break

                event_type = event.get("type", "unknown")
                if event_type == "text":
                    content = event.get('content', '')[:30]
                    logger.debug(f"Yielding text event: {content}...")
                elif event_type == "audio":
                    audio_len = len(event.get("audio", ""))
                    logger.info(f"Yielding audio event: sentence_id={event.get('sentenceId')}, len={audio_len}")
                elif event_type == "error":
                    logger.error(f"SSE error: {event.get('message')}")

                yield event

                if event_type == "done":
                    break

            except Exception as e:
                # Queue.get timeout - just continue waiting
                if "Empty" in str(type(e).__name__):
                    continue
                logger.error(f"Error getting event from queue: {e}")
                break

        # Wait for thread to finish
        thread.join(timeout=1.0)
        logger.info("SSE client finished")
