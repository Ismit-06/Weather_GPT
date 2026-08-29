from datetime import datetime, timezone


def to_float(value):
    if value is None:
        return None

    try:
        return float(value)

    except (TypeError, ValueError):
        return None


def normalize_temperature(value):
    return to_float(value)


def normalize_humidity(value):
    value = to_float(value)

    if value is None:
        return None

    return max(0.0, min(100.0, value))


def normalize_pressure(value):
    return to_float(value)


def normalize_wind_speed(value):
    value = to_float(value)

    if value is None:
        return None

    return max(0.0, value)


def normalize_wind_direction(value):
    value = to_float(value)

    if value is None:
        return None

    return value % 360


def normalize_rainfall(value):
    value = to_float(value)

    if value is None:
        return None

    return max(0.0, value)


def normalize_timestamp(value):

    if isinstance(value, datetime):

        if value.tzinfo is None:
            return value.replace(
                tzinfo=timezone.utc
            )

        return value.astimezone(
            timezone.utc
        )

    if isinstance(value, str):

        try:

            parsed = datetime.fromisoformat(
                value.replace("Z", "+00:00")
            )

            if parsed.tzinfo is None:
                return parsed.replace(
                    tzinfo=timezone.utc
                )

            return parsed.astimezone(
                timezone.utc
            )

        except ValueError:
            pass

    return datetime.now(timezone.utc)
