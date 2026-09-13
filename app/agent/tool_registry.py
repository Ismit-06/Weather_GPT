from dataclasses import dataclass


@dataclass(frozen=True)
class ToolDefinition:
    name: str
    description: str
    intents: tuple[str, ...]


TOOLS = (
    ToolDefinition(
        name="current_weather",
        description="Get current weather conditions.",
        intents=("CURRENT_WEATHER",),
    ),
    ToolDefinition(
        name="hourly_forecast",
        description="Get the hourly weather forecast.",
        intents=("FORECAST",),
    ),
    ToolDefinition(
        name="forecast_at_time",
        description="Get weather for a requested local date/time.",
        intents=(
            "ACTIVITY",
            "TRAVEL",
            "TEMPERATURE",
            "WIND",
            "HUMIDITY",
        ),
    ),
    ToolDefinition(
        name="rain_window",
        description="Find periods where measurable rainfall is forecast.",
        intents=("RAIN",),
    ),
    ToolDefinition(
        name="activity_conditions",
        description="Assess weather suitability for an outdoor activity.",
        intents=("ACTIVITY",),
    ),
    ToolDefinition(
        name="best_activity_time",
        description="Find the best weather window for an activity.",
        intents=("ACTIVITY",),
    ),
    ToolDefinition(
        name="travel_conditions",
        description="Assess weather suitability for driving/travel.",
        intents=("TRAVEL",),
    ),
    ToolDefinition(
        name="hazard_intelligence",
        description="Analyze forecast-derived severe weather signals.",
        intents=("ALERTS", "HAZARDS"),
    ),

    ToolDefinition(
        name="flood_risk",
        description="Estimate flood risk using the existing flood model.",
        intents=("FLOOD",),
    ),

    ToolDefinition(
        name="agriculture",
        description="Assess general weather suitability for farming.",
        intents=("AGRICULTURE",),
    ),

    ToolDefinition(
        name="location_search",
        description="Search and resolve a named location.",
        intents=("LOCATION",),
    ),

    ToolDefinition(
        name="weather_alerts",
        description="Detect severe-weather signals in the forecast.",
        intents=("ALERTS", "FLOOD"),
    ),
)


def get_tools() -> list[ToolDefinition]:
    return list(TOOLS)
