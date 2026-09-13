from enum import Enum
from typing import Any
from pydantic import BaseModel, Field, field_validator


class CloudType(str, Enum):
    clear = "clear"
    cumulus = "cumulus"
    towering_cumulus = "towering_cumulus"
    stratocumulus = "stratocumulus"
    stratus = "stratus"
    altocumulus = "altocumulus"
    altostratus = "altostratus"
    cirrus = "cirrus"
    cirrostratus = "cirrostratus"
    cirrocumulus = "cirrocumulus"
    nimbostratus = "nimbostratus"
    cumulonimbus = "cumulonimbus"
    mixed = "mixed"
    unknown = "unknown"


class CloudThickness(str, Enum):
    thin = "thin"
    moderate = "moderate"
    thick = "thick"
    very_thick = "very_thick"
    unknown = "unknown"


class VerticalDevelopment(str, Enum):
    none = "none"
    low = "low"
    moderate = "moderate"
    strong = "strong"
    extreme = "extreme"
    unknown = "unknown"


class ApparentCloudBase(str, Enum):
    high = "high"
    medium = "medium"
    low = "low"
    very_low = "very_low"
    unknown = "unknown"


class PrecipitationAppearance(str, Enum):
    none_visible = "none_visible"
    possible = "possible"
    likely_visible = "likely_visible"
    unknown = "unknown"


class CloudDevelopmentTrend(str, Enum):
    increasing = "increasing"
    decreasing = "decreasing"
    stable = "stable"
    unknown = "unknown"


class CloudObservation(BaseModel):
    analysis_status: str = Field(default="success", description="'success' or 'uncertain'")
    dominant_cloud_type: CloudType = Field(default=CloudType.unknown)
    secondary_cloud_type: CloudType | None = Field(default=None)
    cloud_coverage: float | None = Field(default=None, ge=0.0, le=1.0)
    cloud_thickness: CloudThickness = Field(default=CloudThickness.unknown)
    vertical_development: VerticalDevelopment = Field(default=VerticalDevelopment.unknown)
    apparent_cloud_base: ApparentCloudBase = Field(default=ApparentCloudBase.unknown)
    convective_appearance: bool | str = Field(default=False)
    precipitation_appearance: PrecipitationAppearance = Field(default=PrecipitationAppearance.none_visible)
    development_trend: CloudDevelopmentTrend = Field(default=CloudDevelopmentTrend.unknown)
    confidence: float = Field(default=0.5, ge=0.0, le=1.0)
    limitations: list[str] = Field(default_factory=list)

    @field_validator("convective_appearance", mode="before")
    @classmethod
    def normalize_convective(cls, v: Any) -> bool | str:
        if isinstance(v, bool):
            return v
        if isinstance(v, str):
            lv = v.strip().lower()
            if lv in ["true", "yes", "1"]:
                return True
            if lv in ["false", "no", "0"]:
                return False
            return "unknown"
        return False


class WeatherContext(BaseModel):
    temperature: float | None = None
    feels_like: float | None = None
    humidity: float | None = None
    pressure: float | None = None
    wind_speed: float | None = None
    wind_direction: float | None = None
    precipitation_probability: float | None = None
    current_precipitation: float | None = None
    forecast_precipitation: float | None = None
    cloud_cover: float | None = None
    condition: str | None = None
    location_name: str | None = None
    source: str = "MET Norway"


class CloudWeatherAssessment(BaseModel):
    next_1_hour: str = Field(description="Nowcasting interpretation (0-1 hour)")
    next_3_hours: str = Field(description="Short-term outlook (1-3 hours)")
    next_6_hours: str = Field(description="Extended short-term outlook (3-6 hours)")
    precipitation_signal: str = Field(description="'low', 'moderate', 'high', 'elevated'")
    convection_signal: str = Field(description="'none', 'low', 'moderate', 'high'")
    weather_change_signal: str = Field(description="'stable', 'moderate', 'significant'")
    overall_short_term_signal: str = Field(description="Summary signal key")
    storm_signal: str = Field(default="low", description="'none', 'low', 'moderate', 'high'")
    confidence: str = Field(description="'High', 'Moderate', 'Low', 'Very Low'")
    is_weather_data_available: bool = Field(default=True)


class VisualCloudIntelligenceResponse(BaseModel):
    status: str = Field(default="success")
    observation: CloudObservation
    weather_context: WeatherContext | None = None
    assessment: CloudWeatherAssessment
    explanation: str
