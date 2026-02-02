"""
LiveKit Token Server
- Validates existing auth tokens
- Issues LiveKit access tokens
"""

import os
import logging
from typing import Optional

from fastapi import FastAPI, HTTPException, Header
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
import httpx
from livekit.api import AccessToken, VideoGrants

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Environment variables
LIVEKIT_API_KEY = os.environ.get("LIVEKIT_API_KEY", "devkey")
LIVEKIT_API_SECRET = os.environ.get("LIVEKIT_API_SECRET", "secret1234567890")
LIVEKIT_URL = os.environ.get("LIVEKIT_URL", "ws://localhost:7880")
AUTH_SERVER_URL = os.environ.get("AUTH_SERVER_URL", "https://api-llmops.banya.ai")

app = FastAPI(title="LiveKit Token Server", version="1.0.0")

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


class TokenRequest(BaseModel):
    room_name: str
    participant_identity: str
    participant_name: str
    conversation_id: Optional[str] = None


class TokenResponse(BaseModel):
    token: str
    url: str
    room_name: str


async def verify_access_token(authorization: str) -> dict:
    """
    Verify the access token with the existing auth server.
    Returns user data if valid.
    """
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Invalid authorization header")

    access_token = authorization[7:]

    # For local development, skip verification if token is "dev"
    if access_token == "dev":
        return {
            "id": "dev-user",
            "name": "Developer",
            "email": "dev@example.com"
        }

    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.get(
                f"{AUTH_SERVER_URL}/api/v1/users/my",
                headers={"Authorization": f"Bearer {access_token}"}
            )

            if response.status_code != 200:
                logger.warning(f"Auth server returned {response.status_code}")
                raise HTTPException(status_code=401, detail="Invalid access token")

            data = response.json()
            return data.get("data", data)

    except httpx.RequestError as e:
        logger.error(f"Auth server request failed: {e}")
        raise HTTPException(status_code=503, detail="Auth server unavailable")


@app.post("/api/v1/livekit/token", response_model=TokenResponse)
async def create_token(
    request: TokenRequest,
    authorization: str = Header(...)
):
    """
    Create a LiveKit access token for the authenticated user.
    """
    # Verify the access token
    user = await verify_access_token(authorization)
    logger.info(f"Creating token for user: {user.get('id', 'unknown')}")

    # Create LiveKit access token
    token = AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET)
    token.with_identity(request.participant_identity)
    token.with_name(request.participant_name)

    # Set room grants
    token.with_grants(VideoGrants(
        room_join=True,
        room=request.room_name,
        can_publish=True,         # Allow publishing audio (mic)
        can_subscribe=True,       # Allow subscribing to AI audio
        can_publish_data=True,    # Allow Data Channel (for text/interrupt)
    ))

    # Set token TTL (1 hour)
    token.with_ttl(3600)

    # Add conversation_id to metadata if provided
    if request.conversation_id:
        token.with_metadata(f'{{"conversation_id": "{request.conversation_id}"}}')

    jwt_token = token.to_jwt()

    logger.info(f"Token created for room: {request.room_name}")

    return TokenResponse(
        token=jwt_token,
        url=LIVEKIT_URL,
        room_name=request.room_name
    )


@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "ok", "service": "livekit-token-server"}


@app.get("/")
async def root():
    """Root endpoint."""
    return {
        "service": "LiveKit Token Server",
        "version": "1.0.0",
        "endpoints": {
            "create_token": "POST /api/v1/livekit/token",
            "health": "GET /health"
        }
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8081)
