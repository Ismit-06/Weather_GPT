from app.services.geocoding import (
    search_location,
)

from app.services.reverse_geocoding import (
    reverse_geocode,
)


async def search_locations(
    query: str,
    language: str = "en",
) -> dict:

    results = await search_location(
        query=query,
        language=language,
        count=5,
    )

    if not results:

        return {
            "status": "not_found",
            "query": query,
            "results": [],
        }

    return {
        "status": "success",
        "query": query,
        "results": results,
    }


async def identify_location(
    latitude: float,
    longitude: float,
) -> dict:

    result = await reverse_geocode(
        latitude=latitude,
        longitude=longitude,
    )

    return {
        "status": "success",
        "location": result,
    }
