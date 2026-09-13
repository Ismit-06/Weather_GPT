from app.hydrology.water_model import (
    CatchmentParameters,
    DEFAULT_PARAMS,
    calculate_effective_rainfall,
    calculate_runoff_volume_m3,
    estimate_stage_rise,
    classify_flood_risk,
)


def predict_future_water_levels(
    rainfall_forecast: list[dict],
    params: CatchmentParameters = DEFAULT_PARAMS,
    current_water_level: float | None = None,
) -> list[dict]:

    if current_water_level is None:
        current_water_level = (
            params.initial_water_level_m
        )

    water_level = float(
        current_water_level
    )

    results = []

    for item in sorted(
        rainfall_forecast,
        key=lambda x: int(x["horizon_hours"])
    ):

        horizon = int(
            item["horizon_hours"]
        )

        rainfall_mm = max(
            0.0,
            float(
                item.get(
                    "predicted_rainfall_mm",
                    0.0
                )
            )
        )

        rain_probability = float(
            item.get(
                "rain_probability_pct",
                0.0
            )
        )

        effective_rainfall = (
            calculate_effective_rainfall(
                rainfall_mm,
                params.runoff_coefficient
            )
        )

        runoff_volume = (
            calculate_runoff_volume_m3(
                effective_rainfall,
                params.catchment_area_km2
            )
        )

        stage_rise = estimate_stage_rise(
            rainfall_mm,
            params
        )

        water_level += stage_rise

        risk = classify_flood_risk(
            water_level,
            params
        )

        threshold_distance = (
            params.flood_warning_level_m
            - water_level
        )

        results.append({
            "horizon_hours": horizon,

            "predicted_rainfall_mm": round(
                rainfall_mm,
                2
            ),

            "rain_probability_pct": round(
                rain_probability,
                2
            ),

            "effective_rainfall_mm": round(
                effective_rainfall,
                2
            ),

            "runoff_m3": round(
                runoff_volume,
                2
            ),

            "water_level_m": round(
                water_level,
                3
            ),

            "stage_rise_m": round(
                stage_rise,
                3
            ),

            "distance_to_warning_level_m": round(
                threshold_distance,
                3
            ),

            "flood_risk": risk,

            "warning_level_m":
                params.flood_warning_level_m,

            "danger_level_m":
                params.flood_danger_level_m,
        })

    return results
