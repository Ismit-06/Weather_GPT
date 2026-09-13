from fastapi import APIRouter, HTTPException, Query

from app.services.geocoding import search_location


router = APIRouter(
    prefix="/location",
    tags=["Location"]
)


@router.get("/search")
async def location_search(
    query: str = Query(
        ...,
        min_length=2,
        max_length=100
    )
):

    query = query.strip()

    if not query:
        raise HTTPException(
            status_code=400,
            detail="Location query cannot be empty."
        )

    try:

        results = await search_location(
            query=query,
            language="en",
            count=5
        )

        # Prefer Indian locations when the query
        # naturally matches India.
        india_results = [
            result
            for result in results
            if result.get("country_code") == "IN"
        ]

        if india_results:
            results = india_results

        return {
            "status": "success",
            "query": query,
            "count": len(results),
            "results": results
        }

    except Exception as exc:

        raise HTTPException(
            status_code=502,
            detail=f"Location search failed: {exc}"
        )
