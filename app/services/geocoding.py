import httpx

OPEN_METEO_URL = "https://geocoding-api.open-meteo.com/v1/search"
NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"


async def search_open_meteo(
    query: str,
    language: str = "en",
    count: int = 5,
) -> list[dict]:

    params = {
        "name": query.strip(),
        "count": min(max(count, 1), 10),
        "language": language,
        "format": "json",
    }

    async with httpx.AsyncClient(
        timeout=15.0,
        headers={
            "User-Agent": "WeatherGPT/1.0"
        }
    ) as client:

        response = await client.get(
            OPEN_METEO_URL,
            params=params
        )

        response.raise_for_status()

        data = response.json()

    results = []

    for item in data.get("results", []):

        results.append({
            "id": item.get("id"),
            "name": item.get("name"),
            "latitude": item.get("latitude"),
            "longitude": item.get("longitude"),
            "elevation": item.get("elevation"),
            "timezone": item.get("timezone"),
            "country": item.get("country"),
            "country_code": item.get("country_code"),
            "admin1": item.get("admin1"),
            "admin2": item.get("admin2"),
            "population": item.get("population"),
            "source": "open_meteo",
        })

    return results


async def search_nominatim(
    query: str,
    count: int = 5,
) -> list[dict]:

    params = {
        "q": query.strip(),
        "format": "jsonv2",
        "limit": min(max(count, 1), 10),
        "addressdetails": 1,
    }

    async with httpx.AsyncClient(
        timeout=15.0,
        headers={
            "User-Agent": "WeatherGPT/1.0 (SIH prototype)"
        }
    ) as client:

        response = await client.get(
            NOMINATIM_URL,
            params=params
        )

        response.raise_for_status()

        data = response.json()

    results = []

    for item in data:

        address = item.get(
            "address",
            {}
        )

        results.append({
            "id": item.get("place_id"),
            "name": item.get(
                "display_name",
                query
            ).split(",")[0],
            "latitude": float(
                item["lat"]
            ),
            "longitude": float(
                item["lon"]
            ),
            "elevation": None,
            "timezone": None,
            "country": address.get(
                "country"
            ),
            "country_code": address.get(
                "country_code",
                ""
            ).upper(),
            "admin1": address.get(
                "state"
            ),
            "admin2": address.get(
                "state_district"
            ),
            "population": None,
            "display_name": item.get(
                "display_name"
            ),
            "source": "nominatim",
        })

    return results


async def search_location(
    query: str,
    language: str = "en",
    count: int = 5,
) -> list[dict]:

    query = query.strip()

    if len(query) < 2:
        return []

    # First try Open-Meteo.
    try:

        results = await search_open_meteo(
            query=query,
            language=language,
            count=count
        )

        if results:
            return results

    except Exception:
        pass

    # Fallback to Nominatim.
    try:

        return await search_nominatim(
            query=query,
            count=count
        )

    except Exception as exc:

        raise RuntimeError(
            f"All location providers failed: {exc}"
        )
