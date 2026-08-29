from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier, RandomForestRegressor
from sklearn.metrics import (
    mean_absolute_error,
    mean_squared_error,
    accuracy_score,
    roc_auc_score,
)
from sqlalchemy.orm import Session

from app.models.features import WeatherFeature


MODEL_DIR = Path("models")
MODEL_DIR.mkdir(exist_ok=True)


FEATURE_COLUMNS = [
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


def load_dataframe(
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
            "for rainfall model training."
        )

    rows = []

    for record in records:

        rows.append({
            "time": record.feature_time,

            "temperature_c": record.temperature_c,
            "temperature_change_1h":
                record.temperature_change_1h,
            "temperature_avg_3h":
                record.temperature_avg_3h,
            "temperature_avg_6h":
                record.temperature_avg_6h,

            "humidity_pct":
                record.humidity_pct,
            "humidity_change_3h":
                record.humidity_change_3h,

            "pressure_hpa":
                record.pressure_hpa,
            "pressure_change_3h":
                record.pressure_change_3h,

            "wind_speed_kmh":
                record.wind_speed_kmh,
            "wind_change_3h":
                record.wind_change_3h,

            "rainfall_mm":
                record.rainfall_mm,
            "rainfall_3h":
                record.rainfall_3h,
            "rainfall_6h":
                record.rainfall_6h,
            "rainfall_24h":
                record.rainfall_24h,
            "rainfall_intensity_1h":
                record.rainfall_intensity_1h,

            "heat_index_c":
                record.heat_index_c,
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

    # Time features.
    df["hour"] = df["time"].dt.hour
    df["day_of_week"] = df["time"].dt.dayofweek
    df["day_of_year"] = df["time"].dt.dayofyear

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


MODEL_FEATURES = FEATURE_COLUMNS + [
    "hour",
    "day_of_week",
    "day_of_year",
    "hour_sin",
    "hour_cos",
    "day_sin",
    "day_cos",
]


def create_targets(
    df: pd.DataFrame,
    horizon: int
) -> pd.DataFrame:

    result = df.copy()

    future_columns = []

    for step in range(1, horizon + 1):

        column = f"future_rain_{step}"

        result[column] = (
            result["rainfall_mm"]
            .shift(-step)
            .clip(lower=0)
        )

        future_columns.append(column)

    # Total rainfall during the future horizon.
    result[f"target_rain_{horizon}h"] = (
        result[future_columns].sum(axis=1)
    )

    # Did any meaningful rain occur during the horizon?
    result[f"target_rain_event_{horizon}h"] = (
        result[future_columns].max(axis=1) >= 0.1
    ).astype(int)

    return result


def train_rainfall_models(
    db: Session,
    location: str
) -> dict:

    base_df = load_dataframe(
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

        df = create_targets(
            base_df,
            horizon
        )

        amount_target = (
            f"target_rain_{horizon}h"
        )

        event_target = (
            f"target_rain_event_{horizon}h"
        )

        required_columns = (
            MODEL_FEATURES
            + [
                amount_target,
                event_target
            ]
        )

        df = df.dropna(
            subset=required_columns
        )

        if len(df) < 60:
            raise ValueError(
                f"Not enough usable samples for "
                f"{horizon}-hour rainfall training."
            )

        split = int(
            len(df) * 0.80
        )

        train = df.iloc[:split]
        test = df.iloc[split:]

        X_train = train[MODEL_FEATURES]
        X_test = test[MODEL_FEATURES]

        y_amount_train = train[
            amount_target
        ]

        y_amount_test = test[
            amount_target
        ]

        y_event_train = train[
            event_target
        ]

        y_event_test = test[
            event_target
        ]

        amount_model = RandomForestRegressor(
            n_estimators=400,
            max_depth=14,
            min_samples_leaf=2,
            max_features="sqrt",
            random_state=42,
            n_jobs=-1
        )

        amount_model.fit(
            X_train,
            y_amount_train
        )

        amount_prediction = amount_model.predict(
            X_test
        )

        amount_prediction = np.clip(
            amount_prediction,
            0,
            None
        )

        amount_mae = mean_absolute_error(
            y_amount_test,
            amount_prediction
        )

        amount_rmse = mean_squared_error(
            y_amount_test,
            amount_prediction
        ) ** 0.5

        # Simple persistence baseline:
        # use the rainfall accumulated during
        # the previous matching horizon.
        baseline_amount = (
            train["rainfall_mm"]
            .rolling(
                horizon,
                min_periods=1
            )
            .sum()
        )

        baseline_amount = (
            baseline_amount
            .reindex(test.index)
            .fillna(0)
            .to_numpy()
        )

        baseline_mae = mean_absolute_error(
            y_amount_test,
            baseline_amount
        )

        # Rain event classifier.
        event_model = RandomForestClassifier(
            n_estimators=400,
            max_depth=12,
            min_samples_leaf=2,
            max_features="sqrt",
            class_weight="balanced",
            random_state=42,
            n_jobs=-1
        )

        event_model.fit(
            X_train,
            y_event_train
        )

        event_prediction = event_model.predict(
            X_test
        )

        event_probability = (
            event_model.predict_proba(X_test)[:, 1]
        )

        event_accuracy = accuracy_score(
            y_event_test,
            event_prediction
        )

        if len(
            np.unique(y_event_test)
        ) == 2:

            event_auc = roc_auc_score(
                y_event_test,
                event_probability
            )

        else:

            event_auc = None

        amount_path = (
            MODEL_DIR
            / (
                f"rainfall_amount_"
                f"{location.lower().replace(' ', '_')}_"
                f"{horizon}h.joblib"
            )
        )

        event_path = (
            MODEL_DIR
            / (
                f"rainfall_event_"
                f"{location.lower().replace(' ', '_')}_"
                f"{horizon}h.joblib"
            )
        )

        joblib.dump(
            {
                "model": amount_model,
                "features": MODEL_FEATURES,
                "location": location,
                "horizon_hours": horizon,
                "mae": float(amount_mae),
                "rmse": float(amount_rmse),
                "baseline_mae": float(baseline_mae),
            },
            amount_path
        )

        joblib.dump(
            {
                "model": event_model,
                "features": MODEL_FEATURES,
                "location": location,
                "horizon_hours": horizon,
                "accuracy": float(event_accuracy),
                "auc": (
                    float(event_auc)
                    if event_auc is not None
                    else None
                ),
            },
            event_path
        )

        results[f"{horizon}h"] = {
            "amount_model": {
                "mae_mm": round(
                    float(amount_mae),
                    4
                ),
                "rmse_mm": round(
                    float(amount_rmse),
                    4
                ),
                "baseline_mae_mm": round(
                    float(baseline_mae),
                    4
                ),
                "training_samples": len(X_train),
                "testing_samples": len(X_test),
            },

            "rain_event_model": {
                "accuracy": round(
                    float(event_accuracy),
                    4
                ),
                "auc": (
                    round(
                        float(event_auc),
                        4
                    )
                    if event_auc is not None
                    else None
                ),
            },

            "amount_model_path": str(
                amount_path
            ),

            "event_model_path": str(
                event_path
            ),
        }

    return results


def latest_feature_row(
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
        "No complete feature record available."
    )


def build_prediction_row(
    record
) -> pd.DataFrame:

    timestamp = pd.Timestamp(
        record.feature_time
    )

    row = {
        "temperature_c":
            record.temperature_c,

        "temperature_change_1h":
            record.temperature_change_1h,

        "temperature_avg_3h":
            record.temperature_avg_3h,

        "temperature_avg_6h":
            record.temperature_avg_6h,

        "humidity_pct":
            record.humidity_pct,

        "humidity_change_3h":
            record.humidity_change_3h,

        "pressure_hpa":
            record.pressure_hpa,

        "pressure_change_3h":
            record.pressure_change_3h,

        "wind_speed_kmh":
            record.wind_speed_kmh,

        "wind_change_3h":
            record.wind_change_3h,

        "rainfall_mm":
            record.rainfall_mm,

        "rainfall_3h":
            record.rainfall_3h,

        "rainfall_6h":
            record.rainfall_6h,

        "rainfall_24h":
            record.rainfall_24h,

        "rainfall_intensity_1h":
            record.rainfall_intensity_1h,

        "heat_index_c":
            record.heat_index_c,

        "hour":
            timestamp.hour,

        "day_of_week":
            timestamp.dayofweek,

        "day_of_year":
            timestamp.dayofyear,
    }

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

    return pd.DataFrame(
        [row],
        columns=MODEL_FEATURES
    )


def predict_rainfall(
    db: Session,
    location: str
) -> dict:

    latest = latest_feature_row(
        db,
        location
    )

    X = build_prediction_row(
        latest
    )

    predictions = []

    for horizon in [1, 3, 6, 12, 24]:

        amount_path = (
            MODEL_DIR
            / (
                f"rainfall_amount_"
                f"{location.lower().replace(' ', '_')}_"
                f"{horizon}h.joblib"
            )
        )

        event_path = (
            MODEL_DIR
            / (
                f"rainfall_event_"
                f"{location.lower().replace(' ', '_')}_"
                f"{horizon}h.joblib"
            )
        )

        if not amount_path.exists():
            raise ValueError(
                f"{horizon}-hour rainfall amount model "
                f"has not been trained."
            )

        if not event_path.exists():
            raise ValueError(
                f"{horizon}-hour rainfall event model "
                f"has not been trained."
            )

        amount_bundle = joblib.load(
            amount_path
        )

        event_bundle = joblib.load(
            event_path
        )

        predicted_amount = float(
            amount_bundle["model"].predict(X)[0]
        )

        predicted_amount = max(
            0.0,
            predicted_amount
        )

        probability = float(
            event_bundle["model"]
            .predict_proba(X)[0][1]
        )

        predictions.append({
            "horizon_hours": horizon,

            "predicted_rainfall_mm":
                round(
                    predicted_amount,
                    2
                ),

            "rain_probability_pct":
                round(
                    probability * 100,
                    2
                ),

            "amount_model_mae_mm":
                round(
                    float(amount_bundle["mae"]),
                    4
                ),

            "amount_baseline_mae_mm":
                round(
                    float(amount_bundle["baseline_mae"]),
                    4
                ),

            "event_model_auc":
                event_bundle["auc"],
        })

    return {
        "location": location,

        "reference_time":
            latest.feature_time,

        "current_rainfall_mm":
            latest.rainfall_mm,

        "forecast":
            predictions,

        "model_type":
            "direct_multi_horizon_random_forest",

        "note": (
            "Rainfall amount is an estimate of accumulated "
            "rainfall during each forecast horizon. "
            "Probability represents the model's estimated "
            "chance of a rainfall event."
        ),
    }
