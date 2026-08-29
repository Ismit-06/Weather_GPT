from datetime import datetime, timezone


def parse_forecast_time(
    value: str
) -> datetime:

    return datetime.fromisoformat(
        value.replace("Z", "+00:00")
    ).astimezone(timezone.utc)


def find_nearest_forecast(
    forecast: list[dict],
    target_local: datetime,
) -> dict | None:

    if not forecast:
        return None

    target_utc = target_local.astimezone(
        timezone.utc
    )

    valid = []

    for item in forecast:

        value = item.get("time")

        if not value:
            continue

        try:

            forecast_time = parse_forecast_time(
                value
            )

            difference = abs(
                (
                    forecast_time -
                    target_utc
                ).total_seconds()
            )

            valid.append(
                (
                    difference,
                    item
                )
            )

        except Exception:
            continue

    if not valid:
        return None

    valid.sort(
        key=lambda pair: pair[0]
    )

    return valid[0][1]
