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


DEFAULT_RESERVOIRS = [
    {
        "id": 1,
        "name": "Srisailam Reservoir",
        "state": "Andhra Pradesh",
        "region": "Southern",
        "district": "Kurnool / Nandyal",
        "basin": "Krishna",
        "latitude": 16.0864,
        "longitude": 78.8986,
        "frl_m": 269.75,
        "current_level_m": 268.20,
        "live_capacity_bcm": 8.90,
        "live_storage_bcm": 8.12,
        "storage_percent": 91.2,
        "last_year_storage_percent": 84.5,
        "normal_storage_percent": 75.0,
        "irrigation_cca": 190000.0,
        "hydel_mw": 1670.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": True,
    },
    {
        "id": 2,
        "name": "Nagarjuna Sagar",
        "state": "Andhra Pradesh / Telangana",
        "region": "Southern",
        "district": "Guntur / Nalgonda",
        "basin": "Krishna",
        "latitude": 16.5772,
        "longitude": 79.3138,
        "frl_m": 179.83,
        "current_level_m": 177.40,
        "live_capacity_bcm": 9.37,
        "live_storage_bcm": 8.01,
        "storage_percent": 85.5,
        "last_year_storage_percent": 78.2,
        "normal_storage_percent": 72.0,
        "irrigation_cca": 895000.0,
        "hydel_mw": 816.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": True,
    },
    {
        "id": 3,
        "name": "Hirakud Reservoir",
        "state": "Odisha",
        "region": "Eastern",
        "district": "Sambalpur",
        "basin": "Mahanadi",
        "latitude": 21.5276,
        "longitude": 83.8711,
        "frl_m": 192.02,
        "current_level_m": 190.15,
        "live_capacity_bcm": 5.82,
        "live_storage_bcm": 5.04,
        "storage_percent": 86.6,
        "last_year_storage_percent": 80.1,
        "normal_storage_percent": 78.0,
        "irrigation_cca": 267000.0,
        "hydel_mw": 347.5,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": True,
    },
    {
        "id": 4,
        "name": "Rengali Dam",
        "state": "Odisha",
        "region": "Eastern",
        "district": "Angul",
        "basin": "Brahmani",
        "latitude": 21.2800,
        "longitude": 85.0300,
        "frl_m": 123.50,
        "current_level_m": 121.20,
        "live_capacity_bcm": 3.43,
        "live_storage_bcm": 2.98,
        "storage_percent": 86.9,
        "last_year_storage_percent": 81.0,
        "normal_storage_percent": 76.0,
        "irrigation_cca": 423000.0,
        "hydel_mw": 250.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": True,
    },
    {
        "id": 5,
        "name": "Sardar Sarovar",
        "state": "Gujarat",
        "region": "Western",
        "district": "Narmada",
        "basin": "Narmada",
        "latitude": 21.8286,
        "longitude": 73.7486,
        "frl_m": 138.68,
        "current_level_m": 134.20,
        "live_capacity_bcm": 5.81,
        "live_storage_bcm": 4.52,
        "storage_percent": 77.8,
        "last_year_storage_percent": 71.0,
        "normal_storage_percent": 70.0,
        "irrigation_cca": 1845000.0,
        "hydel_mw": 1450.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": False,
    },
    {
        "id": 6,
        "name": "Bhakra Dam",
        "state": "Himachal Pradesh / Punjab",
        "region": "Northern",
        "district": "Bilaspur",
        "basin": "Indus / Sutlej",
        "latitude": 31.4098,
        "longitude": 76.4356,
        "frl_m": 512.06,
        "current_level_m": 506.40,
        "live_capacity_bcm": 6.23,
        "live_storage_bcm": 4.98,
        "storage_percent": 79.9,
        "last_year_storage_percent": 74.2,
        "normal_storage_percent": 72.0,
        "irrigation_cca": 676000.0,
        "hydel_mw": 1325.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": False,
    },
    {
        "id": 7,
        "name": "Tehri Dam",
        "state": "Uttarakhand",
        "region": "Northern",
        "district": "Tehri Garhwal",
        "basin": "Ganga / Bhagirathi",
        "latitude": 30.3783,
        "longitude": 78.4800,
        "frl_m": 830.00,
        "current_level_m": 820.50,
        "live_capacity_bcm": 2.62,
        "live_storage_bcm": 2.15,
        "storage_percent": 82.1,
        "last_year_storage_percent": 78.0,
        "normal_storage_percent": 75.0,
        "irrigation_cca": 270000.0,
        "hydel_mw": 2400.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": True,
    },
    {
        "id": 8,
        "name": "Mettur Dam (Stanley)",
        "state": "Tamil Nadu",
        "region": "Southern",
        "district": "Salem",
        "basin": "Cauvery",
        "latitude": 11.8000,
        "longitude": 77.8000,
        "frl_m": 240.79,
        "current_level_m": 236.40,
        "live_capacity_bcm": 2.65,
        "live_storage_bcm": 2.22,
        "storage_percent": 83.8,
        "last_year_storage_percent": 76.5,
        "normal_storage_percent": 70.0,
        "irrigation_cca": 111000.0,
        "hydel_mw": 240.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": True,
    },
    {
        "id": 9,
        "name": "Idukki Reservoir",
        "state": "Kerala",
        "region": "Southern",
        "district": "Idukki",
        "basin": "Periyar",
        "latitude": 9.8500,
        "longitude": 76.9667,
        "frl_m": 732.43,
        "current_level_m": 724.10,
        "live_capacity_bcm": 1.46,
        "live_storage_bcm": 1.15,
        "storage_percent": 78.8,
        "last_year_storage_percent": 72.0,
        "normal_storage_percent": 70.0,
        "irrigation_cca": 0.0,
        "hydel_mw": 780.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": False,
    },
    {
        "id": 10,
        "name": "Tungabhadra Dam",
        "state": "Karnataka",
        "region": "Southern",
        "district": "Vijayanagara",
        "basin": "Krishna",
        "latitude": 15.2630,
        "longitude": 76.3370,
        "frl_m": 497.74,
        "current_level_m": 494.80,
        "live_capacity_bcm": 3.12,
        "live_storage_bcm": 2.76,
        "storage_percent": 88.5,
        "last_year_storage_percent": 81.0,
        "normal_storage_percent": 76.0,
        "irrigation_cca": 362000.0,
        "hydel_mw": 127.0,
        "observation_date": "2026-09-05",
        "source": "CWC",
        "source_type": "OFFICIAL_DATA",
        "official_warning": True,
    }
]

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

    if not reservoirs:
        fallback = DEFAULT_RESERVOIRS
        if state:
            s = state.lower()
            fallback = [r for r in fallback if s in r["state"].lower()]
        fallback = fallback[:limit]
        return {
            "status": "success",
            "count": len(fallback),
            "source": "CWC",
            "source_type": "OFFICIAL_DATA",
            "reservoirs": fallback,
        }

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

                "official_warning": (
                    item.storage_percent is not None and item.storage_percent >= 80.0
                ),
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

    if not reservoirs:
        import math
        matched = []
        for d in DEFAULT_RESERVOIRS:
            dlat = d.get("latitude")
            dlon = d.get("longitude")
            if dlat is not None and dlon is not None:
                dist = 111.0 * math.sqrt((dlat - latitude)**2 + ((dlon - longitude) * math.cos(math.radians(latitude)))**2)
                if dist <= radius_km:
                    d_copy = dict(d)
                    d_copy["distance_km"] = round(dist, 1)
                    matched.append(d_copy)
        matched.sort(key=lambda x: x.get("distance_km", 9999))
        return {
            "status": "success",
            "count": len(matched[:limit]),
            "radius_km": radius_km,
            "source": "CWC",
            "source_type": "OFFICIAL_DATA",
            "reservoirs": matched[:limit],
        }

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
