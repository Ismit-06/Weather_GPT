from __future__ import annotations

import re
from datetime import date, datetime
from pathlib import Path

from pypdf import PdfReader
from sqlalchemy.orm import Session

from app.models.reservoir import Reservoir


SOURCE = "CWC"
SOURCE_TYPE = "OFFICIAL_DATA"


REGION_NAMES = {
    "Northern Region",
    "Eastern Region",
    "Western Region",
    "Central Region",
    "Southern Region",
}


STATE_ALIASES = {
    "Gujrat": "Gujarat",
    "Chattisgarh": "Chhattisgarh",
}


STATE_NAMES = {
    "Andhra Pradesh",
    "Assam",
    "Bihar",
    "Chattisgarh",
    "Chhattisgarh",
    "Goa",
    "Gujarat",
    "Gujrat",
    "Himachal Pradesh",
    "Jharkhand",
    "Karnataka",
    "Kerala",
    "Madhya Pradesh",
    "Maharashtra",
    "Meghalaya",
    "Mizoram",
    "Nagaland",
    "Odisha",
    "Punjab",
    "Rajasthan",
    "Tamil Nadu",
    "Telangana",
    "Tripura",
    "Uttar Pradesh",
    "Uttarakhand",
    "West Bengal",
}


ROW_RE = re.compile(
    r"^(?P<serial>\d+)\s+"
    r"(?P<name>.*?)\s+"
    r"(?P<frl>-?\d+(?:\.\d+)?)\s+"
    r"(?P<level>-?\d+(?:\.\d+)?)\s+"
    r"(?P<capacity>-?\d+(?:\.\d+)?)\s+"
    r"(?P<storage>-?\d+(?:\.\d+)?)\s+"
    r"(?P<date>\d{2}\.\d{2}\.\d{4})\s+"
    r"(?P<current_pct>-?\d+(?:\.\d+)?)\s+"
    r"(?P<last_pct>-?\d+(?:\.\d+)?)\s+"
    r"(?P<normal_pct>-?\d+(?:\.\d+)?)\s+"
    r"(?P<cca>-?\d+(?:\.\d+)?)\s+"
    r"(?P<hydel>-?\d+(?:\.\d+)?)$"
)


NUMERIC_ROW_RE = re.compile(
    r"^-?\d+(?:\.\d+)?\s+"
    r"-?\d+(?:\.\d+)?\s+"
    r"-?\d+(?:\.\d+)?\s+"
    r"-?\d+(?:\.\d+)?\s+"
    r"\d{2}\.\d{2}\.\d{4}\s+"
    r"-?\d+(?:\.\d+)?\s+"
    r"-?\d+(?:\.\d+)?\s+"
    r"-?\d+(?:\.\d+)?\s+"
    r"-?\d+(?:\.\d+)?\s+"
    r"-?\d+(?:\.\d+)?$"
)


def clean_spaces(
    value: str,
) -> str:

    return re.sub(
        r"\s+",
        " ",
        value,
    ).strip()


def parse_float(
    value: str | None,
) -> float | None:

    if value is None:
        return None

    value = value.strip()

    if not value:
        return None

    try:
        return float(
            value.replace(",", "")
        )
    except ValueError:
        return None


def parse_date(
    value: str | None,
) -> date | None:

    if value is None:
        return None

    try:
        return datetime.strptime(
            value.strip(),
            "%d.%m.%Y",
        ).date()

    except ValueError:
        return None


def normalize_state(
    state: str,
) -> str:

    return STATE_ALIASES.get(
        state,
        state,
    )


def parse_complete_row(
    line: str,
    state: str,
    region: str | None,
) -> dict | None:

    line = clean_spaces(
        line
    )

    match = ROW_RE.match(
        line
    )

    if not match:
        return None

    name = clean_spaces(
        match.group("name")
    )

    if not name:
        return None

    return {
        "reservoir_name":
            name,

        "state":
            normalize_state(
                state
            ),

        "region":
            region,

        # The CWC reservoir table does not
        # provide district/basin/coordinates.
        "district":
            None,

        "basin":
            None,

        "latitude":
            None,

        "longitude":
            None,

        "frl_m":
            parse_float(
                match.group("frl")
            ),

        "current_level_m":
            parse_float(
                match.group("level")
            ),

        "live_capacity_bcm":
            parse_float(
                match.group("capacity")
            ),

        "live_storage_bcm":
            parse_float(
                match.group("storage")
            ),

        "storage_percent":
            parse_float(
                match.group("current_pct")
            ),

        "last_year_storage_percent":
            parse_float(
                match.group("last_pct")
            ),

        "normal_storage_percent":
            parse_float(
                match.group("normal_pct")
            ),

        "irrigation_cca":
            parse_float(
                match.group("cca")
            ),

        "hydel_mw":
            parse_float(
                match.group("hydel")
            ),

        "observation_date":
            parse_date(
                match.group("date")
            ),

        "source":
            SOURCE,

        "source_type":
            SOURCE_TYPE,
    }


def extract_cwc_reservoirs_from_pdf(
    pdf_path: str | Path,
) -> list[dict]:

    reader = PdfReader(
        str(pdf_path)
    )

    records: list[dict] = []

    current_region: str | None = None
    current_state: str | None = None

    # CWC bulletin pages 11-15 in the
    # report correspond to PDF pages 13-17.
    first_pdf_page = 13
    last_pdf_page = min(
        17,
        len(reader.pages),
    )

    for pdf_page in range(
        first_pdf_page,
        last_pdf_page + 1,
    ):

        text = (
            reader.pages[
                pdf_page - 1
            ].extract_text()
            or ""
        )

        raw_lines = text.splitlines()

        lines = [
            clean_spaces(line)
            for line in raw_lines
            if clean_spaces(line)
        ]

        i = 0

        while i < len(lines):

            line = lines[i]

            # -------------------------------------------------
            # Region
            # -------------------------------------------------

            if line in REGION_NAMES:

                current_region = line
                current_state = None

                i += 1
                continue

            # -------------------------------------------------
            # State
            # -------------------------------------------------

            if line in STATE_NAMES:

                current_state = normalize_state(
                    line
                )

                i += 1
                continue

            # Ignore headings.
            upper = line.upper()

            if any(
                marker in upper
                for marker in (
                    "REGION/STATE WISE WEEKLY REPORT",
                    "RESERVOIR NAME",
                    "CURRENT RESERVOIR LEVEL",
                    "LIVE CAPACITY",
                    "CURRENT LIVE STORAGE",
                    "STORAGE AS % OF LIVE",
                    "BENEFITS",
                    "SR. NO.",
                    "PAGE ",
                )
            ):

                i += 1
                continue

            # -------------------------------------------------
            # Reservoir rows always start with a serial number.
            # -------------------------------------------------

            serial_match = re.match(
                r"^(\d+)\s+(.*)$",
                line,
            )

            if not serial_match:

                i += 1
                continue

            serial = serial_match.group(
                1
            )

            remainder = clean_spaces(
                serial_match.group(2)
            )

            # -------------------------------------------------
            # Case A:
            # complete row is on one line.
            # -------------------------------------------------

            candidate = (
                f"{serial} {remainder}"
            )

            parsed = parse_complete_row(
                candidate,
                current_state or "Unknown",
                current_region,
            )

            if parsed:

                records.append(
                    parsed
                )

                i += 1
                continue

            # -------------------------------------------------
            # Case B:
            # reservoir name wraps over multiple lines.
            #
            # Example:
            #
            # 1 MAHI BAJAJ
            # SAGAR
            # 280.750 274.550 ...
            # -------------------------------------------------

            parts = [
                remainder
            ]

            j = i + 1

            while j < len(lines):

                next_line = lines[j]

                # Stop if another reservoir starts.
                if re.match(
                    r"^\d+\s+",
                    next_line,
                ):
                    break

                # Stop at a region/state heading.
                if (
                    next_line in REGION_NAMES
                    or next_line in STATE_NAMES
                ):
                    break

                parts.append(
                    next_line
                )

                combined = clean_spaces(
                    " ".join(parts)
                )

                candidate = (
                    f"{serial} {combined}"
                )

                parsed = parse_complete_row(
                    candidate,
                    current_state or "Unknown",
                    current_region,
                )

                if parsed:

                    records.append(
                        parsed
                    )

                    i = j
                    break

                j += 1

            i += 1

    return records


def find_existing(
    db: Session,
    item: dict,
) -> Reservoir | None:

    return (
        db.query(Reservoir)
        .filter(
            Reservoir.reservoir_name
            == item["reservoir_name"],

            Reservoir.state
            == item["state"],
        )
        .first()
    )


def upsert_reservoirs(
    db: Session,
    rows: list[dict],
) -> dict:

    inserted = 0
    updated = 0

    for item in rows:

        existing = find_existing(
            db,
            item,
        )

        if existing is None:

            db.add(
                Reservoir(**item)
            )

            inserted += 1

        else:

            for key, value in item.items():

                # Do not erase useful metadata
                # with None values.
                if value is None:
                    continue

                setattr(
                    existing,
                    key,
                    value,
                )

            updated += 1

    db.commit()

    return {
        "inserted":
            inserted,

        "updated":
            updated,

        "total":
            len(rows),
    }


def ingest_cwc_pdf(
    db: Session,
    pdf_path: str | Path,
) -> dict:

    rows = (
        extract_cwc_reservoirs_from_pdf(
            pdf_path
        )
    )

    if not rows:

        raise ValueError(
            "No reservoir rows could be extracted "
            "from the CWC PDF."
        )

    result = upsert_reservoirs(
        db,
        rows,
    )

    observation_dates = [
        row["observation_date"]
        for row in rows
        if row.get(
            "observation_date"
        ) is not None
    ]

    observation_date = (
        max(
            observation_dates
        ).isoformat()
        if observation_dates
        else None
    )

    return {
        "status":
            "success",

        **result,

        "source":
            SOURCE,

        "source_type":
            SOURCE_TYPE,

        "observation_date":
            observation_date,
    }
