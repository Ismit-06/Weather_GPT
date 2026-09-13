import httpx


NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse"


async def reverse_geocode(
    latitude: float,
    longitude: float,
) -> dict:

    params = {
        "lat": latitude,
        "lon": longitude,
        "format": "jsonv2",
        "zoom": 10,
        "addressdetails": 1,
    }

    headers = {
        "User-Agent": "WeatherGPT/1.0"
    }

    async with httpx.AsyncClient(
        timeout=15.0,
        headers=headers
    ) as client:

        response = await client.get(
            NOMINATIM_URL,
            params=params
        )

        response.raise_for_status()

        data = response.json()

    address = data.get(
        "address",
        {}
    )

    city = (
        address.get("city")
        or address.get("town")
        or address.get("village")
        or address.get("municipality")
        or address.get("county")
        or data.get("name")
        or "Unknown location"
    )

    state = address.get("state")
    country = address.get("country")

    display_parts = [
        city,
        state,
        country,
    ]

    display_name = ", ".join(
        part for part in display_parts
        if part
    )

    return {
        "name": city,
        "state": state,
        "country": country,
        "display_name": display_name,
        "latitude": latitude,
        "longitude": longitude,
    }
