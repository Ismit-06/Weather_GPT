from datetime import date, datetime

from sqlalchemy import (
    Date,
    DateTime,
    Float,
    Integer,
    String,
)

from sqlalchemy.orm import (
    Mapped,
    mapped_column,
)

from app.database import Base


class Reservoir(Base):

    __tablename__ = "reservoirs"

    id: Mapped[int] = mapped_column(
        Integer,
        primary_key=True,
        autoincrement=True,
    )

    reservoir_name: Mapped[str] = mapped_column(
        String(200),
        index=True,
        nullable=False,
    )

    state: Mapped[str | None] = mapped_column(
        String(100),
        index=True,
        nullable=True,
    )

    region: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
    )

    district: Mapped[str | None] = mapped_column(
        String(100),
        nullable=True,
    )

    basin: Mapped[str | None] = mapped_column(
        String(150),
        nullable=True,
    )

    latitude: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    longitude: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    frl_m: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    current_level_m: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    live_capacity_bcm: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    live_storage_bcm: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    storage_percent: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    last_year_storage_percent: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    normal_storage_percent: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    irrigation_cca: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    hydel_mw: Mapped[float | None] = mapped_column(
        Float,
        nullable=True,
    )

    observation_date: Mapped[date | None] = mapped_column(
        Date,
        nullable=True,
    )

    source: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        default="CWC",
    )

    source_type: Mapped[str] = mapped_column(
        String(100),
        nullable=False,
        default="OFFICIAL_DATA",
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        nullable=False,
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime,
        default=datetime.utcnow,
        onupdate=datetime.utcnow,
        nullable=False,
    )
