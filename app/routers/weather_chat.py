from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.agent.weather_agent import WeatherAgent


router = APIRouter(
    prefix="/chat",
    tags=["WeatherGPT Chat"],
)


class WeatherQuestion(BaseModel):

    question: str = Field(
        min_length=1,
        max_length=2000,
    )

    latitude: float = Field(
        ge=-90,
        le=90,
    )

    longitude: float = Field(
        ge=-180,
        le=180,
    )

    language: str = "auto"

    history: list[dict] = Field(
        default_factory=list
    )

    agent_state: dict = Field(
        default_factory=dict
    )


@router.post("/weather")
async def weather_chat(
    payload: WeatherQuestion,
):

    question = payload.question.strip()

    if not question:
        raise HTTPException(
            status_code=400,
            detail="Question cannot be empty.",
        )

    try:

        agent = WeatherAgent(
            timezone_name="Asia/Kolkata"
        )

        result = await agent.analyze(
            question=question,
            latitude=payload.latitude,
            longitude=payload.longitude,
            language=payload.language,
            history=payload.history,
            agent_state=payload.agent_state,
        )

        # Keep the existing API contract understandable
        # while exposing the new agent result.
        return result

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc),
        )

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Weather AI failed: {exc}",
        )
