"""
SSE Client for existing LLM/TTS server
Handles streaming responses from the server
Supports large audio chunks (100KB+)
"""

import asyncio
import json
import logging
from typing import AsyncIterator, Dict, Any

import aiohttp

logger = logging.getLogger(__name__)

# Large buffer size for audio data (1MB)
MAX_LINE_SIZE = 1024 * 1024


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

    async def stream(self, payload: Dict[str, Any]) -> AsyncIterator[Dict[str, Any]]:
        """
        Send a request to the SSE server and stream the response.

        Args:
            payload: Request payload (prompt, tts settings, etc.)

        Yields:
            Parsed SSE events as dictionaries
        """
        headers = {
            "Authorization": f"Bearer {self.auth_token}",
            "Content-Type": "application/json",
            "Accept": "text/event-stream"
        }

        timeout = aiohttp.ClientTimeout(total=300)  # 5 minute timeout

        try:
            async with aiohttp.ClientSession(timeout=timeout) as session:
                async with session.post(
                    self.base_url,
                    json=payload,
                    headers=headers
                ) as response:

                    if response.status != 200:
                        error_text = await response.text()
                        logger.error(f"SSE request failed: {response.status} - {error_text}")
                        yield {"type": "error", "message": f"Server error: {response.status}"}
                        return

                    logger.info(f"SSE response status: {response.status}, content-type: {response.content_type}")

                    # Use chunked reading with manual line buffering for large audio data
                    buffer = ""
                    chunk_count = 0
                    async for chunk in response.content.iter_chunked(8192):
                        chunk_count += 1
                        chunk_str = chunk.decode('utf-8', errors='ignore')
                        buffer += chunk_str

                        if chunk_count <= 3:
                            logger.info(f"Chunk {chunk_count}: {len(chunk)} bytes, buffer size: {len(buffer)}")

                        # Process complete lines
                        while '\n' in buffer:
                            line, buffer = buffer.split('\n', 1)
                            line = line.strip()

                            if not line:
                                continue

                            # SSE format: "data: {...}"
                            if line.startswith('data:'):
                                data_str = line[5:].strip()

                                # Handle [DONE] marker
                                if data_str == "[DONE]":
                                    logger.info("Received [DONE] marker")
                                    yield {"type": "done"}
                                    return

                                try:
                                    event = json.loads(data_str)
                                    event_type = event.get("type", "unknown")
                                    if event_type == "text":
                                        logger.info(f"SSE text event: {event.get('content', '')[:50]}...")
                                    elif event_type == "audio":
                                        audio_len = len(event.get("audio", ""))
                                        logger.info(f"SSE audio event: sentence_id={event.get('sentenceId')}, audio_len={audio_len}")
                                    else:
                                        logger.info(f"SSE event type: {event_type}")
                                    yield event

                                    if event.get("type") == "done":
                                        return

                                except json.JSONDecodeError:
                                    logger.warning(f"Invalid SSE JSON: {data_str[:100]}...")
                                    continue

                            # Some servers send "event:" prefix
                            elif line.startswith('event:'):
                                continue  # We use data-only format

                    # Process remaining buffer
                    logger.info(f"SSE stream ended. Total chunks: {chunk_count}, remaining buffer: {len(buffer)} bytes")
                    if buffer.strip():
                        line = buffer.strip()
                        logger.info(f"Processing remaining buffer line: {line[:100]}...")
                        if line.startswith('data:'):
                            data_str = line[5:].strip()
                            if data_str and data_str != "[DONE]":
                                try:
                                    event = json.loads(data_str)
                                    logger.info(f"Remaining buffer event type: {event.get('type')}")
                                    yield event
                                except json.JSONDecodeError:
                                    logger.warning(f"Failed to parse remaining buffer JSON")

        except aiohttp.ClientError as e:
            logger.error(f"HTTP client error: {e}")
            yield {"type": "error", "message": str(e)}

        except asyncio.TimeoutError:
            logger.error("SSE request timed out")
            yield {"type": "error", "message": "Request timed out"}

        except Exception as e:
            logger.error(f"SSE stream error: {e}")
            yield {"type": "error", "message": str(e)}
