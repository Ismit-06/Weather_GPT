import math
from datetime import datetime, timezone

import httpx


USGS_FEED_URL = (
    "https://earthquake.usgs.gov/"
    "earthquakes/feed/v1.0/summary/"
    "all_day.geojson"
)


def haversine_km(
    lat1: float,
    lon1: float,
    lat2: float,
    lon2: float,
) -> float:

    radius_km = 6371.0

    phi1 = math.radians(lat1)
    phi2 = math.radians(lat2)

    d_phi = math.radians(
        lat2 - lat1
    )

    d_lambda = math.radians(
        lon2 - lon1
    )

    a = (
        math.sin(d_phi / 2) ** 2
        + math.cos(phi1)
        * math.cos(phi2)
        * math.sin(d_lambda / 2) ** 2
    )

    return (
        2
        * radius_km
        * math.asin(
            math.sqrt(a)
        )
    )


def severity_for_event(
    magnitude: float,
    distance_km: float,
) -> str:

    if magnitude >= 6.0:

        if distance_km <= 100:
            return "CRITICAL"

        if distance_km <= 300:
            return "HIGH"

        return "MODERATE"

    if magnitude >= 4.5:

        if distance_km <= 100:
            return "HIGH"

        if distance_km <= 300:
            return "MODERATE"

        return "LOW"

    if magnitude >= 3.0:

        if distance_km <= 50:
            return "MODERATE"

        return "LOW"

    return "LOW"


def parse_event(
    feature: dict,
    user_latitude: float,
    user_longitude: float,
) -> dict | None:

    properties = (
        feature.get("properties")
        or {}
    )

    geometry = (
        feature.get("geometry")
        or {}
    )

    coordinates = (
        geometry.get("coordinates")
        or []
    )

    if len(coordinates) < 3:
        return None

    try:

        longitude = float(
            coordinates[0]
        )

        latitude = float(
            coordinates[1]
        )

        depth_km = float(
            coordinates[2]
        )

    except (
        TypeError,
        ValueError,
    ):

        return None

    magnitude_value = properties.get(
        "mag"
    )

    try:

        magnitude = float(
            magnitude_value
        )

    except (
        TypeError,
        ValueError,
    ):

        magnitude = None

    if magnitude is None:
        return None

    distance_km = haversine_km(
        user_latitude,
        user_longitude,
        latitude,
        longitude,
    )

    event_time_ms = properties.get(
        "time"
    )

    event_time = None

    if event_time_ms is not None:

        try:

            event_time = (
                datetime.fromtimestamp(
                    event_time_ms / 1000.0,
                    tz=timezone.utc,
                ).isoformat()
            )

        except (
            TypeError,
            ValueError,
            OSError,
        ):

            event_time = None

    return {
        "id":
            feature.get("id"),

        "magnitude":
            magnitude,

        "place":
            properties.get(
                "place"
            ),

        "time":
            event_time,

        "latitude":
            latitude,

        "longitude":
            longitude,

        "depth_km":
            depth_km,

        "distance_km":
            round(
                distance_km,
                1,
            ),

        "severity":
            severity_for_event(
                magnitude,
                distance_km,
            ),

        "felt":
            properties.get(
                "felt"
            ),

        "alert":
            properties.get(
                "alert"
            ),

        "status":
            properties.get(
                "status"
            ),

        "url":
            properties.get(
                "url"
            ),

        "source_type":
            "AUTHORITATIVE_EARTHQUAKE_FEED",
    }


async def get_earthquakes(
    latitude: float,
    longitude: float,
    limit: int = 20,
    radius_km: float = 500.0,
) -> dict:

    limit = max(
        1,
        min(limit, 100),
    )

    radius_km = max(
        1.0,
        min(radius_km, 5000.0),
    )

    try:

        async with httpx.AsyncClient(
            timeout=20.0
        ) as client:

            response = await client.get(
                USGS_FEED_URL
            )

            response.raise_for_status()

            data = response.json()

    except Exception as exc:

        return {
            "status":
                "error",

            "message":
                f"Unable to retrieve earthquake data: {exc}",

            "source":
                "USGS",
        }

    events = []

    for feature in (
        data.get("features")
        or []
    ):

        event = parse_event(
            feature=feature,
            user_latitude=latitude,
            user_longitude=longitude,
        )

        if event is None:
            continue

        if (
            event["distance_km"]
            <= radius_km
        ):

            events.append(event)

    events.sort(
        key=lambda event: (
            event["distance_km"],
            -event["magnitude"],
        )
    )

    events = events[:limit]

    critical_count = sum(
        1
        for event in events
        if event["severity"] == "CRITICAL"
    )

    high_count = sum(
        1
        for event in events
        if event["severity"] == "HIGH"
    )

    return {
        "status":
            "success",

        "location": {
            "latitude":
                latitude,

            "longitude":
                longitude,
        },

        "radius_km":
            radius_km,

        "earthquakes":
            events,

        "summary": {
            "total":
                len(events),

            "critical":
                critical_count,

            "high":
                high_count,
        },

        "official_warning":
            False,

        "source_type":
            "AUTHORITATIVE_EARTHQUAKE_FEED",

        "warning_note": (
            "Earthquake events are retrieved from "
            "the USGS real-time feed. Event severity "
            "shown by this application is an application "
            "assessment, not an official earthquake warning."
        ),

        "source":
            "USGS",

        "updated_at":
            data.get("metadata", {})
                .get("generated"),
    }
