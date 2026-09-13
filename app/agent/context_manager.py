from dataclasses import dataclass, replace


@dataclass
class ConversationContext:

    intent: str | None = None

    activity: str | None = None

    target_local_time: str | None = None

    target_date: str | None = None

    timezone: str = "Asia/Kolkata"

    latitude: float | None = None

    longitude: float | None = None

    language: str | None = None

    # Persistent destination/location selected by the user.
    location_name: str | None = None

    location_latitude: float | None = None

    location_longitude: float | None = None

    location_timezone: str | None = None

    location_country: str | None = None

    location_admin1: str | None = None


def update_context(
    previous: ConversationContext,

    *,
    intent: str | None = None,

    activity: str | None = None,

    target_local_time: str | None = None,

    target_date: str | None = None,

    timezone: str | None = None,

    latitude: float | None = None,

    longitude: float | None = None,

    language: str | None = None,

    location_name: str | None = None,

    location_latitude: float | None = None,

    location_longitude: float | None = None,

    location_timezone: str | None = None,

    location_country: str | None = None,

    location_admin1: str | None = None,

    clear_location: bool = False,

) -> ConversationContext:

    return replace(
        previous,

        intent=(
            intent
            if intent
            else previous.intent
        ),

        activity=(
            activity
            if activity
            else previous.activity
        ),

        target_local_time=(
            target_local_time
            if target_local_time
            else previous.target_local_time
        ),

        target_date=(
            target_date
            if target_date
            else previous.target_date
        ),

        timezone=(
            timezone
            if timezone
            else previous.timezone
        ),

        latitude=(
            latitude
            if latitude is not None
            else previous.latitude
        ),

        longitude=(
            longitude
            if longitude is not None
            else previous.longitude
        ),

        language=(
            language
            if language
            else previous.language
        ),

        location_name=(
            None
            if clear_location
            else (
                location_name
                if location_name
                else previous.location_name
            )
        ),

        location_latitude=(
            None
            if clear_location
            else (
                location_latitude
                if location_latitude is not None
                else previous.location_latitude
            )
        ),

        location_longitude=(
            None
            if clear_location
            else (
                location_longitude
                if location_longitude is not None
                else previous.location_longitude
            )
        ),

        location_timezone=(
            None
            if clear_location
            else (
                location_timezone
                if location_timezone
                else previous.location_timezone
            )
        ),

        location_country=(
            None
            if clear_location
            else (
                location_country
                if location_country
                else previous.location_country
            )
        ),

        location_admin1=(
            None
            if clear_location
            else (
                location_admin1
                if location_admin1
                else previous.location_admin1
            )
        ),
    )
