#!/usr/bin/env python3
"""Test SSE server response format"""
import asyncio
import aiohttp
import json
import os

SSE_SERVER_URL = os.environ.get("SSE_SERVER_URL", "http://210.109.53.87:8083/completion-with-tts")
SSE_AUTH_TOKEN = os.environ.get("SSE_AUTH_TOKEN", "sk-f930a9588d02ec45d3fbfbc8788d90f12d652d74ff13657f")

async def test_sse():
    headers = {
        "Authorization": f"Bearer {SSE_AUTH_TOKEN}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream"
    }

    payload = {
        "prompt": "hello",
        "stream": True,
        "tts": {
            "enabled": True,
            "voiceName": "ko-KR-Wavenet-A"
        }
    }

    timeout = aiohttp.ClientTimeout(total=60)

    async with aiohttp.ClientSession(timeout=timeout) as session:
        async with session.post(SSE_SERVER_URL, json=payload, headers=headers) as response:
            print(f"Status: {response.status}")
            print(f"Content-Type: {response.content_type}")
            print(f"Headers: {dict(response.headers)}")
            print("---")

            chunk_count = 0
            total_bytes = 0
            async for chunk in response.content.iter_chunked(8192):
                chunk_count += 1
                total_bytes += len(chunk)
                chunk_str = chunk.decode('utf-8', errors='ignore')

                print(f"\n=== Chunk {chunk_count} ({len(chunk)} bytes) ===")
                # Print first 500 chars of each chunk
                print(chunk_str[:500])
                if len(chunk_str) > 500:
                    print(f"... (truncated, total {len(chunk_str)} chars)")

                # Check for newlines
                newline_count = chunk_str.count('\n')
                print(f"Newlines in chunk: {newline_count}")

                if chunk_count >= 5:
                    print("\n... stopping after 5 chunks")
                    break

            print(f"\nTotal: {chunk_count} chunks, {total_bytes} bytes")

if __name__ == "__main__":
    asyncio.run(test_sse())
