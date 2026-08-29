from pathlib import Path
from tempfile import NamedTemporaryFile

from fastapi import (
    APIRouter,
    Depends,
    File,
    Query,
    UploadFile,
    HTTPException,
)

from sqlalchemy.orm import Session

from app.database import get_db
from app.models.reservoir import Reservoir
from app.services.cwc_reservoirs import (
    ingest_cwc_pdf,
)


router = APIRouter(
    prefix="/dams",
    tags=["Dam / Reservoir Intelligence"],
)


@router.post("/ingest")
async def ingest_dams(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
):

    filename = (
        file.filename
        or ""
    ).lower()

    if not filename.endswith(
        ".pdf"
    ):

        raise HTTPException(
            status_code=400,
            detail=(
                "Please upload an official "
                "CWC Reservoir Storage Bulletin PDF."
            ),
        )

    content = await file.read()

    if not content:

        raise HTTPException(
            status_code=400,
            detail="Uploaded file is empty.",
        )

    temp_path = None

    try:

        with NamedTemporaryFile(
            suffix=".pdf",
            delete=False,
        ) as temp:

            temp.write(
                content
            )

            temp_path = Path(
                temp.name
            )

        return ingest_cwc_pdf(
            db=db,
            pdf_path=temp_path,
        )

    except ValueError as exc:

        raise HTTPException(
            status_code=400,
            detail=str(exc),
        )

    finally:

        if (
            temp_path is not None
            and temp_path.exists()
        ):

            temp_path.unlink()


@router.get("")
def list_dams(
    state: str | None = Query(
        None,
        min_length=2,
        max_length=100,
    ),

    limit: int = Query(
        50,
        ge=1,
        le=500,
    ),

    db: Session = Depends(get_db),
):

    query = db.query(
        Reservoir
    )

    if state:

        query = query.filter(
            Reservoir.state.ilike(
                f"%{state}%"
            )
        )

    reservoirs = (
        query
        .order_by(
            Reservoir.reservoir_name
        )
        .limit(limit)
        .all()
    )

    return {
        "status":
            "success",

        "count":
            len(reservoirs),

        "source":
            "CWC",

        "source_type":
            "OFFICIAL_DATA",

        "reservoirs": [
            {
                "id":
                    item.id,

                "name":
                    item.reservoir_name,

                "state":
                    item.state,

                "region":
                    item.region,

                "district":
                    item.district,

                "basin":
                    item.basin,

                "latitude":
                    item.latitude,

                "longitude":
                    item.longitude,

                "frl_m":
                    item.frl_m,

                "current_level_m":
                    item.current_level_m,

                "live_capacity_bcm":
                    item.live_capacity_bcm,

                "live_storage_bcm":
                    item.live_storage_bcm,

                "storage_percent":
                    item.storage_percent,

                "last_year_storage_percent":
                    item.last_year_storage_percent,

                "normal_storage_percent":
                    item.normal_storage_percent,

                "irrigation_cca":
                    item.irrigation_cca,

                "hydel_mw":
                    item.hydel_mw,

                "observation_date":
                    (
                        item.observation_date.isoformat()
                        if item.observation_date
                        else None
                    ),

                "source":
                    item.source,

                "source_type":
                    item.source_type,

                "official_warning":
                    False,
            }

            for item in reservoirs
        ],
    }


@router.get("/nearby")
def nearby_dams(
    latitude: float = Query(
        ...,
        ge=-90,
        le=90,
    ),

    longitude: float = Query(
        ...,
        ge=-180,
        le=180,
    ),

    radius_km: float = Query(
        150,
        gt=0,
        le=1000,
    ),

    limit: int = Query(
        20,
        ge=1,
        le=100,
    ),

    db: Session = Depends(get_db),
):

    reservoirs = (
        db.query(Reservoir)
        .filter(
            Reservoir.latitude.is_not(None),
            Reservoir.longitude.is_not(None),
        )
        .all()
    )

    # CWC bulletin currently does not provide
    # reservoir coordinates in this table.
    # Therefore this endpoint intentionally
    # returns no geospatial matches until an
    # authoritative coordinate source is added.
    return {
        "status":
            "success",

        "count":
            0,

        "radius_km":
            radius_km,

        "source":
            "CWC",

        "source_type":
            "OFFICIAL_DATA",

        "reservoirs":
            [],
        
        "message": (
            "The current CWC bulletin does not "
            "contain reservoir coordinates, so "
            "nearby matching is unavailable until "
            "an authoritative coordinate dataset "
            "is linked."
        ),
    }
