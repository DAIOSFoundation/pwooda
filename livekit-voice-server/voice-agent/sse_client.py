"""
SSE Client for existing LLM/TTS server
Handles streaming responses from the server
"""

import json
import logging
from typing import AsyncIterator, Dict, Any

import aiohttp

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

                    # Parse SSE stream
                    async for line in response.content:
                        line = line.decode('utf-8').strip()

                        if not line:
                            continue

                        # SSE format: "data: {...}"
                        if line.startswith('data:'):
                            data_str = line[5:].strip()

                            # Handle [DONE] marker
                            if data_str == "[DONE]":
                                yield {"type": "done"}
                                break

                            try:
                                event = json.loads(data_str)
                                yield event

                                if event.get("type") == "done":
                                    break

                            except json.JSONDecodeError:
                                logger.warning(f"Invalid SSE JSON: {data_str}")
                                continue

                        # Some servers send "event:" prefix
                        elif line.startswith('event:'):
                            continue  # We use data-only format

        except aiohttp.ClientError as e:
            logger.error(f"HTTP client error: {e}")
            yield {"type": "error", "message": str(e)}

        except asyncio.TimeoutError:
            logger.error("SSE request timed out")
            yield {"type": "error", "message": "Request timed out"}

        except Exception as e:
            logger.error(f"SSE stream error: {e}")
            yield {"type": "error", "message": str(e)}


# Import asyncio for timeout error handling
import asyncio
