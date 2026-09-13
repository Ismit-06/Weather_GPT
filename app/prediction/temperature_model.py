from pathlib import Path

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import mean_absolute_error, mean_squared_error
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


def _load_dataframe(
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

    if len(records) < 24:
        raise ValueError(
            "At least 24 hourly feature records are required "
            "to train the temperature model."
        )

    rows = []

    for record in records:
        rows.append({
            "time": record.feature_time,

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

    # Target = temperature one hour in the future.
    df["target_temperature_1h"] = (
        df["temperature_c"].shift(-1)
    )

    # Remove rows where the target is unavailable.
    df = df.dropna(
        subset=["target_temperature_1h"]
    )

    # Fill missing engineered features using forward/backward fill.
    df[FEATURE_COLUMNS] = (
        df[FEATURE_COLUMNS]
        .ffill()
        .bfill()
    )

    # Drop rows that still contain missing values.
    df = df.dropna(
        subset=FEATURE_COLUMNS
    )

    return df


def train_temperature_model(
    db: Session,
    location: str
) -> dict:

    df = _load_dataframe(
        db,
        location
    )

    if len(df) < 30:
        raise ValueError(
            "At least 30 usable hourly samples are recommended "
            "for the first training run."
        )

    X = df[FEATURE_COLUMNS]
    y = df["target_temperature_1h"]

    # Chronological split.
    split_index = int(
        len(df) * 0.80
    )

    if split_index <= 0 or split_index >= len(df):
        raise ValueError(
            "Unable to create training and testing datasets."
        )

    X_train = X.iloc[:split_index]
    X_test = X.iloc[split_index:]

    y_train = y.iloc[:split_index]
    y_test = y.iloc[split_index:]

    model = RandomForestRegressor(
        n_estimators=300,
        max_depth=12,
        min_samples_leaf=2,
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

    # Simple persistence baseline:
    # next hour = current temperature.
    baseline_predictions = X_test[
        "temperature_c"
    ].to_numpy()

    baseline_mae = mean_absolute_error(
        y_test,
        baseline_predictions
    )

    model_path = (
        MODEL_DIR
        / f"temperature_{location.lower().replace(' ', '_')}.joblib"
    )

    joblib.dump(
        {
            "model": model,
            "features": FEATURE_COLUMNS,
            "location": location,
            "horizon_hours": 1,
            "mae": float(mae),
            "rmse": float(rmse),
            "baseline_mae": float(baseline_mae),
        },
        model_path
    )

    return {
        "location": location,
        "training_samples": len(X_train),
        "testing_samples": len(X_test),
        "mae_celsius": round(float(mae), 4),
        "rmse_celsius": round(float(rmse), 4),
        "baseline_mae_celsius": round(
            float(baseline_mae),
            4
        ),
        "model_path": str(model_path),
        "model": "RandomForestRegressor",
        "horizon": "1 hour",
    }


def predict_next_temperature(
    db: Session,
    location: str
) -> dict:

    model_path = (
        MODEL_DIR
        / f"temperature_{location.lower().replace(' ', '_')}.joblib"
    )

    if not model_path.exists():
        raise ValueError(
            "Temperature model has not been trained yet. "
            "Run POST /prediction/temperature/train first."
        )

    bundle = joblib.load(model_path)

    # We need the latest FEATURE ROW that has all model inputs.
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

    if not records:
        raise ValueError(
            "No feature data found for this location."
        )

    selected = None

    for record in records:

        row = {
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
        }

        if all(
            value is not None
            for value in row.values()
        ):
            selected = record
            break

    if selected is None:
        raise ValueError(
            "No complete feature record is available. "
            "Ingest historical weather data and regenerate "
            "features before predicting."
        )

    row = {
        "temperature_c": selected.temperature_c,
        "temperature_change_1h": selected.temperature_change_1h,
        "temperature_avg_3h": selected.temperature_avg_3h,
        "temperature_avg_6h": selected.temperature_avg_6h,
        "humidity_pct": selected.humidity_pct,
        "humidity_change_3h": selected.humidity_change_3h,
        "pressure_hpa": selected.pressure_hpa,
        "pressure_change_3h": selected.pressure_change_3h,
        "wind_speed_kmh": selected.wind_speed_kmh,
        "wind_change_3h": selected.wind_change_3h,
        "rainfall_mm": selected.rainfall_mm,
        "rainfall_3h": selected.rainfall_3h,
        "rainfall_6h": selected.rainfall_6h,
        "rainfall_24h": selected.rainfall_24h,
        "rainfall_intensity_1h": selected.rainfall_intensity_1h,
        "heat_index_c": selected.heat_index_c,
    }

    X = pd.DataFrame(
        [row],
        columns=bundle["features"]
    )

    prediction = bundle["model"].predict(X)[0]

    return {
        "location": location,
        "reference_time": selected.feature_time,
        "horizon_hours": 1,
        "current_temperature_c": selected.temperature_c,
        "predicted_temperature_c": round(
            float(prediction),
            2
        ),
        "model_mae_celsius": bundle["mae"],
        "baseline_mae_celsius": bundle["baseline_mae"],
        "confidence_note": (
            "This is a model prediction. "
            "MAE is reported from the held-out chronological test set."
        ),
        "model": "RandomForestRegressor",
    }
