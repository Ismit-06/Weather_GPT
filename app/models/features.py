from datetime import datetime

from sqlalchemy import DateTime, Float, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class WeatherFeature(Base):
    __tablename__ = "weather_features"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True
    )

    location_name: Mapped[str] = mapped_column(
        String(150),
        nullable=False,
        index=True
    )

    latitude: Mapped[float] = mapped_column(
        Float,
        nullable=False
    )

    longitude: Mapped[float] = mapped_column(
        Float,
        nullable=False
    )

    feature_time: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        index=True
    )

    temperature_c: Mapped[float | None] = mapped_column(Float)

    temperature_change_1h: Mapped[float | None] = mapped_column(Float)
    temperature_avg_3h: Mapped[float | None] = mapped_column(Float)
    temperature_avg_6h: Mapped[float | None] = mapped_column(Float)

    humidity_pct: Mapped[float | None] = mapped_column(Float)
    humidity_change_3h: Mapped[float | None] = mapped_column(Float)

    pressure_hpa: Mapped[float | None] = mapped_column(Float)
    pressure_change_3h: Mapped[float | None] = mapped_column(Float)

    wind_speed_kmh: Mapped[float | None] = mapped_column(Float)
    wind_change_3h: Mapped[float | None] = mapped_column(Float)

    rainfall_mm: Mapped[float | None] = mapped_column(Float)
    rainfall_3h: Mapped[float | None] = mapped_column(Float)
    rainfall_6h: Mapped[float | None] = mapped_column(Float)
    rainfall_24h: Mapped[float | None] = mapped_column(Float)

    rainfall_intensity_1h: Mapped[float | None] = mapped_column(Float)

    heat_index_c: Mapped[float | None] = mapped_column(Float)

    source: Mapped[str] = mapped_column(
        String(100),
        nullable=False
    )
