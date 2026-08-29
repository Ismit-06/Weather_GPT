from pathlib import Path

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error
from sqlalchemy.orm import Session

from app.models.features import WeatherFeature


MODEL_DIR = Path("models")
MODEL_DIR.mkdir(exist_ok=True)

BASE_FEATURES = [
    "temperature_c",
    "temperature_change_1h",
    "temperature_avg_3h",
    "temperature_avg_6h",
    "humidity_pct",
    "humidity_change_3h",
    "pressure_hpa",
    "pressure_change_3h",
    "wind_speed_kmh",
    "wind_change_3h",
    "rainfall_mm",
    "rainfall_3h",
    "rainfall_6h",
    "rainfall_24h",
    "rainfall_intensity_1h",
    "heat_index_c",
]


def build_dataframe(
    db: Session,
    location: str
) -> pd.DataFrame:

    records = (
        db.query(WeatherFeature)
        .filter(
            WeatherFeature.location_name == location
        )
        .order_by(
            WeatherFeature.feature_time.asc()
        )
        .all()
    )

    if len(records) < 72:
        raise ValueError(
            "At least 72 hourly feature records are required "
            "for multi-horizon temperature training."
        )

    rows = []

    for record in records:

        timestamp = pd.Timestamp(
            record.feature_time
        )

        rows.append({
            "time": timestamp,

            "temperature_c": record.temperature_c,
            "temperature_change_1h": record.temperature_change_1h,
            "temperature_avg_3h": record.temperature_avg_3h,
            "temperature_avg_6h": record.temperature_avg_6h,

            "humidity_pct": record.humidity_pct,
            "humidity_change_3h": record.humidity_change_3h,

            "pressure_hpa": record.pressure_hpa,
            "pressure_change_3h": record.pressure_change_3h,

            "wind_speed_kmh": record.wind_speed_kmh,
            "wind_change_3h": record.wind_change_3h,

            "rainfall_mm": record.rainfall_mm,
            "rainfall_3h": record.rainfall_3h,
            "rainfall_6h": record.rainfall_6h,
            "rainfall_24h": record.rainfall_24h,
            "rainfall_intensity_1h": record.rainfall_intensity_1h,

            "heat_index_c": record.heat_index_c,
        })

    df = pd.DataFrame(rows)

    df["time"] = pd.to_datetime(
        df["time"],
        utc=True
    )

    df = (
        df.sort_values("time")
        .drop_duplicates("time")
        .reset_index(drop=True)
    )

    # Time features
    df["hour"] = df["time"].dt.hour
    df["day_of_week"] = df["time"].dt.dayofweek
    df["day_of_year"] = df["time"].dt.dayofyear

    # Cyclical encoding for daily and yearly patterns.
    import numpy as np

    df["hour_sin"] = np.sin(
        2 * np.pi * df["hour"] / 24
    )

    df["hour_cos"] = np.cos(
        2 * np.pi * df["hour"] / 24
    )

    df["day_sin"] = np.sin(
        2 * np.pi * df["day_of_year"] / 365.25
    )

    df["day_cos"] = np.cos(
        2 * np.pi * df["day_of_year"] / 365.25
    )

    return df


MODEL_FEATURES = BASE_FEATURES + [
    "hour",
    "day_of_week",
    "day_of_year",
    "hour_sin",
    "hour_cos",
    "day_sin",
    "day_cos",
]


def train_direct_models(
    db: Session,
    location: str
) -> dict:

    df = build_dataframe(
        db,
        location
    )

    results = {}

    horizons = [
        1,
        3,
        6,
        12,
        24
    ]

    for horizon in horizons:

        working = df.copy()

        target_column = (
            f"target_temperature_{horizon}h"
        )

        working[target_column] = (
            working["temperature_c"]
            .shift(-horizon)
        )

        working = working.dropna(
            subset=MODEL_FEATURES + [
                target_column
            ]
        )

        if len(working) < 50:
            raise ValueError(
                f"Not enough usable samples for "
                f"{horizon}-hour training."
            )

        split = int(
            len(working) * 0.80
        )

        train = working.iloc[:split]
        test = working.iloc[split:]

        X_train = train[MODEL_FEATURES]
        y_train = train[target_column]

        X_test = test[MODEL_FEATURES]
        y_test = test[target_column]

        model = RandomForestRegressor(
            n_estimators=400,
            max_depth=14,
            min_samples_leaf=2,
            max_features="sqrt",
            random_state=42,
            n_jobs=-1
        )

        model.fit(
            X_train,
            y_train
        )

        predictions = model.predict(
            X_test
        )

        mae = mean_absolute_error(
            y_test,
            predictions
        )

        rmse = mean_squared_error(
            y_test,
            predictions
        ) ** 0.5

        # Persistence baseline:
        # future temperature = latest known temperature.
        baseline = X_test[
            "temperature_c"
        ].to_numpy()

        baseline_mae = mean_absolute_error(
            y_test,
            baseline
        )

        model_path = (
            MODEL_DIR
            / (
                f"temperature_direct_"
                f"{location.lower().replace(' ', '_')}_"
                f"{horizon}h.joblib"
            )
        )

        joblib.dump(
            {
                "model": model,
                "features": MODEL_FEATURES,
                "location": location,
                "horizon_hours": horizon,
                "mae": float(mae),
                "rmse": float(rmse),
                "baseline_mae": float(
                    baseline_mae
                ),
            },
            model_path
        )

        results[f"{horizon}h"] = {
            "training_samples": len(X_train),
            "testing_samples": len(X_test),
            "mae_celsius": round(
                float(mae),
                4
            ),
            "rmse_celsius": round(
                float(rmse),
                4
            ),
            "baseline_mae_celsius": round(
                float(baseline_mae),
                4
            ),
            "model_path": str(model_path)
        }

    return results


def latest_complete_row(
    db: Session,
    location: str
):

    records = (
        db.query(WeatherFeature)
        .filter(
            WeatherFeature.location_name == location
        )
        .order_by(
            WeatherFeature.feature_time.desc()
        )
        .all()
    )

    for record in records:

        values = [
            record.temperature_c,
            record.temperature_change_1h,
            record.temperature_avg_3h,
            record.temperature_avg_6h,
            record.humidity_pct,
            record.humidity_change_3h,
            record.pressure_hpa,
            record.pressure_change_3h,
            record.wind_speed_kmh,
            record.wind_change_3h,
            record.rainfall_mm,
            record.rainfall_3h,
            record.rainfall_6h,
            record.rainfall_24h,
            record.rainfall_intensity_1h,
            record.heat_index_c,
        ]

        if all(
            value is not None
            for value in values
        ):
            return record

    raise ValueError(
        "No complete feature record is available."
    )


def predict_direct_temperature(
    db: Session,
    location: str
) -> dict:

    latest = latest_complete_row(
        db,
        location
    )

    timestamp = pd.Timestamp(
        latest.feature_time
    )

    row = {
        "temperature_c": latest.temperature_c,
        "temperature_change_1h":
            latest.temperature_change_1h,
        "temperature_avg_3h":
            latest.temperature_avg_3h,
        "temperature_avg_6h":
            latest.temperature_avg_6h,
        "humidity_pct": latest.humidity_pct,
        "humidity_change_3h":
            latest.humidity_change_3h,
        "pressure_hpa": latest.pressure_hpa,
        "pressure_change_3h":
            latest.pressure_change_3h,
        "wind_speed_kmh":
            latest.wind_speed_kmh,
        "wind_change_3h":
            latest.wind_change_3h,
        "rainfall_mm":
            latest.rainfall_mm,
        "rainfall_3h":
            latest.rainfall_3h,
        "rainfall_6h":
            latest.rainfall_6h,
        "rainfall_24h":
            latest.rainfall_24h,
        "rainfall_intensity_1h":
            latest.rainfall_intensity_1h,
        "heat_index_c":
            latest.heat_index_c,

        "hour": timestamp.hour,
        "day_of_week": timestamp.dayofweek,
        "day_of_year": timestamp.dayofyear,
    }

    import numpy as np

    row["hour_sin"] = np.sin(
        2 * np.pi * row["hour"] / 24
    )

    row["hour_cos"] = np.cos(
        2 * np.pi * row["hour"] / 24
    )

    row["day_sin"] = np.sin(
        2 * np.pi * row["day_of_year"] / 365.25
    )

    row["day_cos"] = np.cos(
        2 * np.pi * row["day_of_year"] / 365.25
    )

    predictions = []

    for horizon in [1, 3, 6, 12, 24]:

        model_path = (
            MODEL_DIR
            / (
                f"temperature_direct_"
                f"{location.lower().replace(' ', '_')}_"
                f"{horizon}h.joblib"
            )
        )

        if not model_path.exists():
            raise ValueError(
                f"{horizon}-hour temperature model "
                f"has not been trained."
            )

        bundle = joblib.load(
            model_path
        )

        X = pd.DataFrame(
            [row],
            columns=bundle["features"]
        )

        prediction = float(
            bundle["model"].predict(X)[0]
        )

        predictions.append({
            "hour_ahead": horizon,
            "predicted_temperature_c":
                round(prediction, 2),
            "model_mae_celsius":
                round(
                    float(bundle["mae"]),
                    4
                ),
            "baseline_mae_celsius":
                round(
                    float(bundle["baseline_mae"]),
                    4
                ),
        })

    return {
        "location": location,
        "reference_time": latest.feature_time,
        "current_temperature_c":
            latest.temperature_c,
        "forecast": predictions,
        "model_type":
            "direct_multi_horizon_random_forest",
        "warning": (
            "Predictions are model estimates. "
            "Uncertainty varies by horizon and "
            "weather regime."
        )
    }
