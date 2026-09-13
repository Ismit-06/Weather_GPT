from datetime import datetime, timezone


NUMERIC_FIELDS = [
    "temperature_c",
    "rainfall_mm",
    "humidity_pct",
    "wind_speed_ms",
    "wind_direction_deg",
]


def parse_time(value: str) -> datetime:
    return datetime.fromisoformat(
        value.replace("Z", "+00:00")
    ).astimezone(timezone.utc)


def interpolate_value(
    first: float | None,
    second: float | None,
    ratio: float,
) -> float | None:

    if first is None:
        return second

    if second is None:
        return first

    return first + (
        second - first
    ) * ratio


def interpolate_forecast(
    forecast: list[dict],
    target_local: datetime,
) -> dict | None:

    if not forecast:
        return None

    target_utc = target_local.astimezone(
        timezone.utc
    )

    points = []

    for item in forecast:

        value = item.get("time")

        if not value:
            continue

        try:
            points.append(
                (
                    parse_time(value),
                    item,
                )
            )
        except Exception:
            continue

    points.sort(
        key=lambda pair: pair[0]
    )

    if not points:
        return None

    # Exact match.
    for timestamp, item in points:

        if timestamp == target_utc:
            return {
                **item,
                "interpolated": False,
            }

    # Find surrounding points.
    before = None
    after = None

    for timestamp, item in points:

        if timestamp < target_utc:
            before = (
                timestamp,
                item,
            )

        elif timestamp > target_utc:
            after = (
                timestamp,
                item,
            )
            break

    # Outside available range.
    if before is None:
        item = points[0][1]

        return {
            **item,
            "interpolated": False,
        }

    if after is None:
        item = points[-1][1]

        return {
            **item,
            "interpolated": False,
        }

    before_time, before_item = before
    after_time, after_item = after

    total_seconds = (
        after_time -
        before_time
    ).total_seconds()

    elapsed_seconds = (
        target_utc -
        before_time
    ).total_seconds()

    ratio = (
        elapsed_seconds /
        total_seconds
        if total_seconds > 0
        else 0.0
    )

    result = {
        "time": target_utc.isoformat(),
        "interpolated": True,
        "source_before": before_item.get("time"),
        "source_after": after_item.get("time"),
    }

    for field in NUMERIC_FIELDS:

        result[field] = interpolate_value(
            before_item.get(field),
            after_item.get(field),
            ratio,
        )

    # Keep categorical weather state from the closer point.
    if ratio < 0.5:
        result["condition"] = before_item.get(
            "condition"
        )
    else:
        result["condition"] = after_item.get(
            "condition"
        )

    return result
