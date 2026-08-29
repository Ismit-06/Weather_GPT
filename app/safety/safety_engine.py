from dataclasses import dataclass


@dataclass
class SafetyResult:
    category: str
    score: float
    level: str
    actions: list[str]
    reasons: list[str]


def clamp(value: float) -> float:
    return max(0.0, min(100.0, float(value)))


def level_from_score(score: float) -> str:
    if score >= 80:
        return "SEVERE"
    if score >= 60:
        return "HIGH"
    if score >= 30:
        return "MODERATE"
    return "LOW"


def weighted_overall_risk(
    hazard_scores: dict[str, float]
) -> float:

    weights = {
        "HEAVY_RAIN": 0.25,
        "EXTREME_HEAT": 0.20,
        "STRONG_WIND": 0.20,
        "THUNDERSTORM": 0.20,
        "FLOOD": 0.15,
    }

    total = 0.0
    used_weight = 0.0

    for hazard, weight in weights.items():

        if hazard in hazard_scores:
            total += (
                clamp(hazard_scores[hazard])
                * weight
            )
            used_weight += weight

    if used_weight == 0:
        return 0.0

    return clamp(total / used_weight)


def travel_safety(
    heavy_rain: float,
    flood: float,
    thunderstorm: float,
    strong_wind: float,
) -> SafetyResult:

    score = (
        heavy_rain * 0.35
        + flood * 0.35
        + thunderstorm * 0.20
        + strong_wind * 0.10
    )

    score = clamp(score)

    actions = []
    reasons = []

    if heavy_rain >= 60:
        actions.append(
            "Avoid unnecessary travel during the peak rainfall period."
        )
        reasons.append(
            "Heavy-rain risk is elevated."
        )

    if flood >= 60:
        actions.append(
            "Avoid low-lying and waterlogged roads."
        )
        reasons.append(
            "Flood risk is elevated."
        )

    if thunderstorm >= 60:
        actions.append(
            "Avoid exposed outdoor locations during thunderstorms."
        )
        reasons.append(
            "Thunderstorm risk is elevated."
        )

    if strong_wind >= 60:
        actions.append(
            "Avoid areas with unsecured objects, trees or structures."
        )
        reasons.append(
            "Strong-wind risk is elevated."
        )

    if not actions:
        actions.append(
            "Normal travel precautions are appropriate."
        )

    return SafetyResult(
        category="TRAVEL",
        score=round(score, 2),
        level=level_from_score(score),
        actions=actions,
        reasons=reasons
    )


def outdoor_safety(
    heat: float,
    thunderstorm: float,
    strong_wind: float,
) -> SafetyResult:

    score = (
        heat * 0.45
        + thunderstorm * 0.35
        + strong_wind * 0.20
    )

    score = clamp(score)

    actions = []
    reasons = []

    if heat >= 60:
        actions.append(
            "Reduce prolonged outdoor activity and stay hydrated."
        )
        reasons.append(
            "Heat-stress risk is elevated."
        )

    if thunderstorm >= 60:
        actions.append(
            "Move indoors when thunderstorm activity increases."
        )
        reasons.append(
            "Thunderstorm risk is elevated."
        )

    if strong_wind >= 60:
        actions.append(
            "Avoid exposed areas and unstable structures."
        )
        reasons.append(
            "Strong-wind risk is elevated."
        )

    if not actions:
        actions.append(
            "Outdoor conditions are currently within the model's lower-risk range."
        )

    return SafetyResult(
        category="OUTDOOR",
        score=round(score, 2),
        level=level_from_score(score),
        actions=actions,
        reasons=reasons
    )


def flood_safety(
    flood_score: float,
) -> SafetyResult:

    score = clamp(flood_score)

    actions = []
    reasons = []

    if score >= 80:
        actions.extend([
            "Avoid flood-prone and low-lying areas.",
            "Do not cross flowing or flooded roads.",
            "Follow official emergency instructions."
        ])

        reasons.append(
            "Predicted flood risk is severe."
        )

    elif score >= 60:
        actions.extend([
            "Avoid unnecessary travel through low-lying areas.",
            "Monitor local flood and weather warnings."
        ])

        reasons.append(
            "Predicted flood risk is high."
        )

    elif score >= 30:
        actions.append(
            "Monitor rainfall and water-level changes."
        )

        reasons.append(
            "Flood conditions require monitoring."
        )

    else:
        actions.append(
            "No significant flood action is indicated by the current model."
        )

    return SafetyResult(
        category="FLOOD",
        score=round(score, 2),
        level=level_from_score(score),
        actions=actions,
        reasons=reasons
    )


def agriculture_safety(
    heat: float,
    heavy_rain: float,
    wind: float,
) -> SafetyResult:

    score = (
        heat * 0.35
        + heavy_rain * 0.40
        + wind * 0.25
    )

    score = clamp(score)

    actions = []
    reasons = []

    if heavy_rain >= 60:
        actions.append(
            "Avoid unnecessary field operations during intense rainfall."
        )
        reasons.append(
            "Heavy-rain conditions may affect field access."
        )

    if heat >= 60:
        actions.append(
            "Schedule outdoor farm work during cooler periods."
        )
        reasons.append(
            "Heat stress is elevated."
        )

    if wind >= 60:
        actions.append(
            "Protect exposed equipment and monitor crop conditions."
        )
        reasons.append(
            "Strong-wind risk is elevated."
        )

    if not actions:
        actions.append(
            "No major weather-related farm action is indicated by the current model."
        )

    return SafetyResult(
        category="AGRICULTURE",
        score=round(score, 2),
        level=level_from_score(score),
        actions=actions,
        reasons=reasons
    )


def build_safety_profile(
    hazard_scores: dict[str, float]
) -> dict:

    overall = weighted_overall_risk(
        hazard_scores
    )

    travel = travel_safety(
        heavy_rain=hazard_scores.get(
            "HEAVY_RAIN",
            0
        ),
        flood=hazard_scores.get(
            "FLOOD",
            0
        ),
        thunderstorm=hazard_scores.get(
            "THUNDERSTORM",
            0
        ),
        strong_wind=hazard_scores.get(
            "STRONG_WIND",
            0
        ),
    )

    outdoor = outdoor_safety(
        heat=hazard_scores.get(
            "EXTREME_HEAT",
            0
        ),
        thunderstorm=hazard_scores.get(
            "THUNDERSTORM",
            0
        ),
        strong_wind=hazard_scores.get(
            "STRONG_WIND",
            0
        ),
    )

    flood = flood_safety(
        flood_score=hazard_scores.get(
            "FLOOD",
            0
        )
    )

    agriculture = agriculture_safety(
        heat=hazard_scores.get(
            "EXTREME_HEAT",
            0
        ),
        heavy_rain=hazard_scores.get(
            "HEAVY_RAIN",
            0
        ),
        wind=hazard_scores.get(
            "STRONG_WIND",
            0
        ),
    )

    profiles = [
        travel,
        outdoor,
        flood,
        agriculture,
    ]

    highest_hazard = None

    if hazard_scores:
        highest_hazard = max(
            hazard_scores,
            key=hazard_scores.get
        )

    return {
        "overall_risk_score": round(
            overall,
            2
        ),
        "overall_risk_level":
            level_from_score(overall),

        "highest_hazard":
            highest_hazard,

        "recommendations": [
            {
                "category":
                    item.category,

                "score":
                    item.score,

                "level":
                    item.level,

                "actions":
                    item.actions,

                "reasons":
                    item.reasons,
            }
            for item in profiles
        ],

        "engine":
            "safety_engine_v1",
    }
