from app.database import SessionLocal

from app.hydrology.future_water import (
    predict_future_water_levels,
)

from app.hydrology.water_model import (
    DEFAULT_PARAMS,
)

from app.prediction.rainfall_model import (
    predict_rainfall,
)


async def get_flood_risk(
    latitude: float,
    longitude: float,
    location: str = "Current location",
) -> dict:

    db = SessionLocal()

    try:

        try:

            rainfall_result = predict_rainfall(
                db,
                location,
            )

            rainfall_forecast = (
                rainfall_result.get(
                    "forecast",
                    [],
                )
            )

            water_forecast = (
                predict_future_water_levels(
                    rainfall_forecast,
                    DEFAULT_PARAMS,
                )
            )

            return {
                "status": "success",

                "location":
                    location,

                "latitude":
                    latitude,

                "longitude":
                    longitude,

                "flood_risk":
                    _extract_risk(
                        water_forecast
                    ),

                "current_water_level_m":
                    DEFAULT_PARAMS.initial_water_level_m,

                "warning_level_m":
                    DEFAULT_PARAMS.flood_warning_level_m,

                "danger_level_m":
                    DEFAULT_PARAMS.flood_danger_level_m,

                "rainfall":
                    rainfall_result,

                "water_forecast":
                    water_forecast,

                "source_type":
                    "MODEL_ESTIMATE",

                "official_warning":
                    False,

                "warning_note": (
                    "Flood risk is a prototype model estimate "
                    "based on rainfall-runoff and catchment "
                    "parameters. It is not an official flood "
                    "warning or direct river-gauge measurement."
                ),

                "engine":
                    "rainfall_runoff_flood_v1",
            }

        except ValueError as exc:

            return {
                "status":
                    "model_unavailable",

                "location":
                    location,

                "latitude":
                    latitude,

                "longitude":
                    longitude,

                "flood_risk":
                    None,

                "source_type":
                    "MODEL_ESTIMATE",

                "official_warning":
                    False,

                "message":
                    str(exc),

                "warning_note": (
                    "The configured flood model does not "
                    "have sufficient database data for this "
                    "location. No flood risk value is being "
                    "invented."
                ),
            }

    finally:

        db.close()


def _extract_risk(
    forecast: list | dict,
) -> str:

    items = (
        forecast
        if isinstance(forecast, list)
        else [forecast]
    )

    levels = []

    for item in items:

        if not isinstance(item, dict):
            continue

        for key in (
            "risk",
            "flood_risk",
            "level",
        ):

            value = item.get(key)

            if isinstance(
                value,
                str,
            ):

                levels.append(
                    value.upper()
                )

    if "SEVERE" in levels:
        return "SEVERE"

    if "HIGH" in levels:
        return "HIGH"

    if "MODERATE" in levels:
        return "MODERATE"

    return "LOW"
