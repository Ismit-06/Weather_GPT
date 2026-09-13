from dataclasses import dataclass


@dataclass
class HazardResult:
    hazard: str
    score: float
    level: str
    reasons: list[str]


def clamp(
    value: float,
    minimum: float = 0.0,
    maximum: float = 100.0
) -> float:

    return max(
        minimum,
        min(maximum, value)
    )


def risk_level(score: float) -> str:

    if score >= 80:
        return "SEVERE"

    if score >= 60:
        return "HIGH"

    if score >= 30:
        return "MODERATE"

    return "LOW"


def heavy_rain_risk(
    rainfall_24h: float | None,
    rainfall_3h: float | None,
    rain_probability: float | None = None,
) -> HazardResult:

    rainfall_24h = rainfall_24h or 0.0
    rainfall_3h = rainfall_3h or 0.0
    rain_probability = rain_probability or 0.0

    score = 0.0
    reasons = []

    # Prototype thresholds.
    if rainfall_3h >= 50:
        score += 55
        reasons.append(
            "Very high short-duration rainfall"
        )

    elif rainfall_3h >= 25:
        score += 40
        reasons.append(
            "High short-duration rainfall"
        )

    elif rainfall_3h >= 10:
        score += 20
        reasons.append(
            "Increasing short-duration rainfall"
        )

    if rainfall_24h >= 100:
        score += 35
        reasons.append(
            "Very high 24-hour accumulation"
        )

    elif rainfall_24h >= 50:
        score += 25
        reasons.append(
            "High 24-hour accumulation"
        )

    if rain_probability >= 80:
        score += 15
        reasons.append(
            "High rainfall probability"
        )

    elif rain_probability >= 60:
        score += 8
        reasons.append(
            "Moderate-to-high rainfall probability"
        )

    score = clamp(score)

    return HazardResult(
        hazard="HEAVY_RAIN",
        score=round(score, 2),
        level=risk_level(score),
        reasons=reasons
    )


def heat_risk(
    temperature_c: float | None,
    heat_index_c: float | None,
) -> HazardResult:

    temperature_c = temperature_c or 0.0
    heat_index_c = (
        heat_index_c
        if heat_index_c is not None
        else temperature_c
    )

    score = 0.0
    reasons = []

    if heat_index_c >= 45:
        score = 95
        reasons.append(
            "Extremely high heat index"
        )

    elif heat_index_c >= 40:
        score = 75
        reasons.append(
            "Very high heat index"
        )

    elif heat_index_c >= 35:
        score = 50
        reasons.append(
            "High heat index"
        )

    elif heat_index_c >= 32:
        score = 30
        reasons.append(
            "Elevated heat stress potential"
        )

    if temperature_c >= 40:
        score += 10
        reasons.append(
            "Air temperature is very high"
        )

    score = clamp(score)

    return HazardResult(
        hazard="EXTREME_HEAT",
        score=round(score, 2),
        level=risk_level(score),
        reasons=reasons
    )


def strong_wind_risk(
    wind_speed_kmh: float | None,
) -> HazardResult:

    wind_speed_kmh = wind_speed_kmh or 0.0

    score = 0.0
    reasons = []

    if wind_speed_kmh >= 90:
        score = 95
        reasons.append(
            "Extremely strong wind"
        )

    elif wind_speed_kmh >= 70:
        score = 75
        reasons.append(
            "Very strong wind"
        )

    elif wind_speed_kmh >= 50:
        score = 55
        reasons.append(
            "Strong wind"
        )

    elif wind_speed_kmh >= 30:
        score = 30
        reasons.append(
            "Elevated wind speed"
        )

    score = clamp(score)

    return HazardResult(
        hazard="STRONG_WIND",
        score=round(score, 2),
        level=risk_level(score),
        reasons=reasons
    )


def thunderstorm_risk(
    rainfall_mm: float | None,
    wind_speed_kmh: float | None,
    pressure_change_3h: float | None,
) -> HazardResult:

    rainfall_mm = rainfall_mm or 0.0
    wind_speed_kmh = wind_speed_kmh or 0.0
    pressure_change_3h = (
        pressure_change_3h or 0.0
    )

    score = 0.0
    reasons = []

    if rainfall_mm >= 5:
        score += 25
        reasons.append(
            "Recent rainfall activity"
        )

    if wind_speed_kmh >= 35:
        score += 30
        reasons.append(
            "Strong wind associated with storm potential"
        )

    elif wind_speed_kmh >= 20:
        score += 15
        reasons.append(
            "Increasing wind activity"
        )

    if pressure_change_3h <= -3:
        score += 30
        reasons.append(
            "Rapid pressure fall"
        )

    elif pressure_change_3h <= -1.5:
        score += 15
        reasons.append(
            "Pressure is falling"
        )

    score = clamp(score)

    return HazardResult(
        hazard="THUNDERSTORM",
        score=round(score, 2),
        level=risk_level(score),
        reasons=reasons
    )


def calculate_hazard_profile(
    temperature_c: float | None,
    heat_index_c: float | None,
    rainfall_3h: float | None,
    rainfall_24h: float | None,
    rainfall_probability: float | None,
    wind_speed_kmh: float | None,
    pressure_change_3h: float | None,
) -> list[HazardResult]:

    results = []

    results.append(
        heavy_rain_risk(
            rainfall_24h=rainfall_24h,
            rainfall_3h=rainfall_3h,
            rain_probability=rainfall_probability
        )
    )

    results.append(
        heat_risk(
            temperature_c=temperature_c,
            heat_index_c=heat_index_c
        )
    )

    results.append(
        strong_wind_risk(
            wind_speed_kmh=wind_speed_kmh
        )
    )

    results.append(
        thunderstorm_risk(
            rainfall_mm=rainfall_3h,
            wind_speed_kmh=wind_speed_kmh,
            pressure_change_3h=pressure_change_3h
        )
    )

    return sorted(
        results,
        key=lambda result: result.score,
        reverse=True
    )
