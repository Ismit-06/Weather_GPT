def valid_temperature(value):
    return value is None or -80 <= value <= 70


def valid_humidity(value):
    return value is None or 0 <= value <= 100


def valid_pressure(value):
    return value is None or 800 <= value <= 1100


def valid_wind_speed(value):
    return value is None or 0 <= value <= 400


def valid_wind_direction(value):
    return value is None or 0 <= value <= 360


def valid_rainfall(value):
    return value is None or 0 <= value <= 2000


def validate_observation(data):
    errors = []

    if not valid_temperature(data.get("temperature_c")):
        errors.append("temperature_out_of_range")

    if not valid_humidity(data.get("humidity_pct")):
        errors.append("humidity_out_of_range")

    if not valid_pressure(data.get("pressure_hpa")):
        errors.append("pressure_out_of_range")

    if not valid_wind_speed(data.get("wind_speed_kmh")):
        errors.append("wind_speed_out_of_range")

    if not valid_wind_direction(data.get("wind_direction_deg")):
        errors.append("wind_direction_out_of_range")

    if not valid_rainfall(data.get("rainfall_mm")):
        errors.append("rainfall_out_of_range")

    return len(errors) == 0, errors
