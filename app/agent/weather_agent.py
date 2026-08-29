from dataclasses import asdict
from datetime import datetime

from app.agent.context_manager import (
    ConversationContext,
    update_context,
)

from app.agent.entity_resolver import (
    resolve_query,
    is_current_location_request,
)

from app.agent.tool_router import (
    run_weather_tool,
)

from app.services.sarvam_chat import (
    chat as sarvam_chat,
)

from app.services.sarvam_language import (
    detect_language,
)

from app.services.weather_intent import (
    detect_weather_intent,
)

from app.services.weather_time import (
    normalize_time,
)

from app.tools.best_time import (
    get_best_activity_time,
)

from app.tools.location import (
    search_locations,
)


class WeatherAgent:

    def __init__(
        self,
        timezone_name: str = "Asia/Kolkata",
    ):
        self.context = ConversationContext(
            timezone=timezone_name
        )

    def restore_context_from_history(
        self,
        history: list[dict] | None,
    ) -> None:

        if not history:
            return

        # Walk newest messages first.
        for item in reversed(history):

            if not isinstance(item, dict):
                continue

            content = item.get("content")

            if not isinstance(content, str):
                continue

            text = content.strip()

            if not text:
                continue

            # Recover activity from previous conversation.
            if self.context.activity is None:

                lower = text.lower()

                activity = None

                if (
                    "running" in lower
                    or "jogging" in lower
                    or "jog" in lower
                ):
                    activity = "running"

                elif (
                    "cycling" in lower
                    or "cycle" in lower
                    or "bike" in lower
                ):
                    activity = "cycling"

                elif (
                    "walking" in lower
                    or "walk" in lower
                ):
                    activity = "walking"

                elif (
                    "hiking" in lower
                    or "hike" in lower
                ):
                    activity = "hiking"

                elif "cricket" in lower:
                    activity = "cricket"

                elif (
                    "football" in lower
                    or "soccer" in lower
                ):
                    activity = "football"

                elif (
                    "swimming" in lower
                    or "swim" in lower
                ):
                    activity = "swimming"

                if activity:
                    self.context = update_context(
                        self.context,
                        activity=activity,
                    )

            # Recover a useful intent.
            if self.context.intent is None:

                detected = detect_weather_intent(
                    text
                )

                self.context = update_context(
                    self.context,
                    intent=detected,
                )

            if (
                self.context.activity is not None
                and self.context.intent is not None
            ):
                break


    async def analyze(
        self,
        question: str,
        latitude: float,
        longitude: float,
        language: str = "auto",
        history: list[dict] | None = None,
        agent_state: dict | None = None,
    ) -> dict:

        question = question.strip()

        if not question:
            raise ValueError(
                "Question cannot be empty."
            )

        # Restore structured conversation context
        # from the history supplied by the client.
        self.restore_context_from_history(
            history
        )

        # Prefer explicit machine-readable state
        # over parsing previous assistant prose.
        if agent_state:

            self.context = update_context(
                self.context,

                intent=
                    agent_state.get("intent"),

                activity=
                    agent_state.get("activity"),

                target_local_time=
                    agent_state.get(
                        "target_local_time"
                    ),

                target_date=
                    agent_state.get(
                        "target_date"
                    ),

                timezone=
                    agent_state.get("timezone"),

                latitude=
                    agent_state.get("latitude"),

                longitude=
                    agent_state.get("longitude"),

                language=
                    agent_state.get("language"),

                location_name=
                    agent_state.get(
                        "location_name"
                    ),

                location_latitude=
                    agent_state.get(
                        "location_latitude"
                    ),

                location_longitude=
                    agent_state.get(
                        "location_longitude"
                    ),

                location_timezone=
                    agent_state.get(
                        "location_timezone"
                    ),

                location_country=
                    agent_state.get(
                        "location_country"
                    ),

                location_admin1=
                    agent_state.get(
                        "location_admin1"
                    ),
            )

        # -----------------------------------------------------
        # Language
        # -----------------------------------------------------

        detected_language = None
        language_code = None
        script_code = None

        if (
            not language
            or language.lower() == "auto"
        ):

            detection = await detect_language(
                question
            )

            detected_language = detection.get(
                "language",
                "English",
            )

            language_code = detection.get(
                "language_code",
                "en-IN",
            )

            script_code = detection.get(
                "script_code"
            )

        else:

            detected_language = language

        # -----------------------------------------------------
        # Intent
        # -----------------------------------------------------

        intent = detect_weather_intent(
            question
        )

        # -----------------------------------------------------
        # Resolve entities / follow-ups
        # -----------------------------------------------------

        resolved = resolve_query(
            question,
            self.context,
        )

        # -----------------------------------------------------
        # Resolve a named destination/location.
        # -----------------------------------------------------

        active_latitude = latitude
        active_longitude = longitude
        active_timezone = (
            self.context.timezone
            or "Asia/Kolkata"
        )

        requested_location = None

        # -----------------------------------------------------
        # Explicit request for the device/current location.
        # -----------------------------------------------------

        if is_current_location_request(question):

            # Discard the remembered destination.
            self.context = update_context(
                self.context,
                clear_location=True,
            )

            requested_location = {
                "name":
                    "Current location",

                "latitude":
                    latitude,

                "longitude":
                    longitude,

                "timezone":
                    self.context.timezone
                    or "Asia/Kolkata",

                "country":
                    None,

                "admin1":
                    None,
            }

            active_latitude = latitude
            active_longitude = longitude
            active_timezone = (
                self.context.timezone
                or "Asia/Kolkata"
            )

        # -----------------------------------------------------
        # Explicit destination in the current question.
        # -----------------------------------------------------

        elif resolved.location_text:

            location_result = await search_locations(
                query=resolved.location_text,
                language="en",
            )

            matches = location_result.get(
                "results",
                [],
            )

            if not matches:

                return {
                    "status":
                        "needs_clarification",

                    "type":
                        "LOCATION_NOT_FOUND",

                    "question":
                        question,

                    "location":
                        resolved.location_text,

                    "clarification": (
                        f"I couldn't find "
                        f"{resolved.location_text}. "
                        f"Could you provide another "
                        f"location?"
                    ),

                    "language":
                        detected_language,

                    "language_code":
                        language_code,

                    "script_code":
                        script_code,

                    "context":
                        asdict(
                            self.context
                        ),
                }

            requested_location = matches[0]

            active_latitude = float(
                requested_location[
                    "latitude"
                ]
            )

            active_longitude = float(
                requested_location[
                    "longitude"
                ]
            )

            active_timezone = (
                requested_location.get(
                    "timezone"
                )
                or "Asia/Kolkata"
            )

            self.context = update_context(
                self.context,

                location_name=
                    requested_location.get(
                        "name"
                    ),

                location_latitude=
                    active_latitude,

                location_longitude=
                    active_longitude,

                location_timezone=
                    active_timezone,

                location_country=
                    requested_location.get(
                        "country"
                    ),

                location_admin1=
                    requested_location.get(
                        "admin1"
                    ),
            )

        # -----------------------------------------------------
        # No new location: reuse the previous destination.
        # -----------------------------------------------------

        elif (
            self.context.location_latitude
            is not None
            and self.context.location_longitude
            is not None
        ):

            active_latitude = (
                self.context.location_latitude
            )

            active_longitude = (
                self.context.location_longitude
            )

            active_timezone = (
                self.context.location_timezone
                or self.context.timezone
                or "Asia/Kolkata"
            )

            requested_location = {
                "name":
                    self.context.location_name,

                "latitude":
                    active_latitude,

                "longitude":
                    active_longitude,

                "timezone":
                    active_timezone,

                "country":
                    self.context.location_country,

                "admin1":
                    self.context.location_admin1,
            }

        # A short follow-up such as "What about 8 PM?"
        # may not contain a weather keyword. In that case,
        # inherit the previous conversational intent.
        if (
            self.context.intent
            and intent == "CURRENT_WEATHER"
            and (
                resolved.time_text is not None
                or resolved.day_text is not None
                or resolved.uses_previous_activity
                or resolved.uses_previous_time
                or resolved.uses_previous_day
            )
        ):
            intent = self.context.intent

        # -----------------------------------------------------
        # Detect best-time requests
        # -----------------------------------------------------

        lower_question = (
            question.lower()
        )

        best_time_request = any(
            phrase in lower_question
            for phrase in [
                "best time",
                "best time to",
                "best time for",
                "good time to",
                "ideal time",
                "when should i",
                "when is the best",
                "what time should i",
            ]
        )

        # -----------------------------------------------------
        # Best-time tool
        # -----------------------------------------------------

        if best_time_request:

            activity = (
                resolved.activity
                or self.context.activity
            )

            if activity is None:

                return {
                    "status":
                        "needs_clarification",

                    "type":
                        "MISSING_ACTIVITY",

                    "question":
                        question,

                    "clarification": (
                        "What activity are you planning? "
                        "For example, running, cycling, "
                        "walking, or cricket."
                    ),

                    "language":
                        detected_language,

                    "language_code":
                        language_code,

                    "script_code":
                        script_code,

                    "context":
                        asdict(
                            self.context
                        ),
                }

            date_text = (
                resolved.day_text
                or "tomorrow"
            )

            result = (
                await get_best_activity_time(
                    latitude=active_latitude,
                    longitude=active_longitude,
                    activity=activity,
                    date_text=date_text,
                    timezone_name=self.context.timezone,
                )
            )

            self.context = update_context(
                self.context,
                intent="ACTIVITY",
                activity=activity,
                latitude=latitude,
                longitude=longitude,
                language=detected_language,
            )

            if result.get("best_window"):

                best_window = (
                    result["best_window"]
                )

                self.context = update_context(
                    self.context,
                    target_local_time=
                        best_window.get(
                            "start"
                        ),
                )

            return {
                "status":
                    result.get(
                        "status",
                        "success",
                    ),

                "type":
                    "BEST_TIME",

                "question":
                    question,

                "intent":
                    "ACTIVITY",

                "activity":
                    activity,

                "date":
                    result.get("date"),

                "recommendation":
                    result.get(
                        "recommendation"
                    ),

                "best_window":
                    result.get(
                        "best_window"
                    ),

                "other_windows":
                    result.get(
                        "other_windows",
                        [],
                    ),

                "source":
                    result.get(
                        "source"
                    ),

                "updated_at":
                    result.get(
                        "updated_at"
                    ),

                "language":
                    detected_language,

                "language_code":
                    language_code,

                "script_code":
                    script_code,

                "context":
                    asdict(
                        self.context
                    ),
            }

        # -----------------------------------------------------
        # Ambiguous time
        # -----------------------------------------------------

        if resolved.ambiguous_time:

            return {
                "status":
                    "needs_clarification",

                "type":
                    "AMBIGUOUS_TIME",

                "question":
                    question,

                "clarification": (
                    f"Do you mean "
                    f"{resolved.ambiguous_time} AM "
                    f"or "
                    f"{resolved.ambiguous_time} PM?"
                ),

                "language":
                    detected_language,

                "language_code":
                    language_code,

                "script_code":
                    script_code,

                "context":
                    asdict(
                        self.context
                    ),
            }

        # -----------------------------------------------------
        # Resolve target date/time.
        #
        # target_date and target_local_time are separate:
        #
        #   "tomorrow"        -> date only
        #   "tomorrow 8 PM"  -> date + time
        #   "What about 8?"  -> previous date + new time
        #   "Sunday"          -> new date + previous time
        # -----------------------------------------------------

        target_local_time = None
        previous_target = None

        if self.context.target_local_time:

            try:

                previous_target = datetime.fromisoformat(
                    self.context.target_local_time
                )

            except ValueError:

                previous_target = None

        target_date = (
            self.context.target_date
        )

        is_current_weather = (
            intent == "CURRENT_WEATHER"
        )

        has_explicit_time = (
            resolved.time_text is not None
        )

        has_explicit_day = (
            resolved.day_text is not None
        )

        # -----------------------------------------------------
        # Explicit day.
        # -----------------------------------------------------

        if has_explicit_day:

            # Resolve the calendar date without requiring a
            # clock time.
            day_target = normalize_time(
                time_text=None,
                day_text=resolved.day_text,
                timezone_name=self.context.timezone,
                default_hour=0,
            )

            target_date = day_target.date().isoformat()

        # -----------------------------------------------------
        # Explicit time.
        # -----------------------------------------------------

        if has_explicit_time and not is_current_weather:

            if (
                resolved.day_text is None
                and previous_target is not None
            ):

                # "What about 8 PM?"
                # Keep the previous date when a complete
                # previous datetime exists.
                target = normalize_time(
                    time_text=resolved.time_text,
                    day_text=None,
                    timezone_name=self.context.timezone,
                )

                target = target.replace(
                    year=previous_target.year,
                    month=previous_target.month,
                    day=previous_target.day,
                )

            elif (
                resolved.day_text is None
                and self.context.target_date is not None
            ):

                # Previous request was date-only:
                # "weather in Hyderabad tomorrow"
                #
                # Preserve that stored date when the user
                # follows up with only a time.
                target = normalize_time(
                    time_text=resolved.time_text,
                    day_text=None,
                    timezone_name=self.context.timezone,
                )

                try:

                    year, month, day = map(
                        int,
                        self.context.target_date.split("-"),
                    )

                except ValueError:
                    raise ValueError(
                        "Invalid stored target date."
                    )

                target = target.replace(
                    year=year,
                    month=month,
                    day=day,
                )

            else:

                # "Tomorrow at 8 PM"
                # or "Sunday 8 PM".
                target = normalize_time(
                    time_text=resolved.time_text,
                    day_text=resolved.day_text,
                    timezone_name=self.context.timezone,
                )

            target_local_time = (
                target.isoformat()
            )

            target_date = (
                target.date().isoformat()
            )

        # -----------------------------------------------------
        # Day-only follow-up.
        # -----------------------------------------------------

        elif (
            has_explicit_day
            and not has_explicit_time
            and previous_target is not None
            and resolved.uses_previous_time
            and not is_current_weather
        ):

            # "Same thing Sunday"
            # New day, same hour/minute.
            target = normalize_time(
                time_text=None,
                day_text=resolved.day_text,
                timezone_name=self.context.timezone,
                default_hour=previous_target.hour,
            )

            target = target.replace(
                minute=previous_target.minute,
                second=previous_target.second,
                microsecond=previous_target.microsecond,
            )

            target_local_time = (
                target.isoformat()
            )

            target_date = (
                target.date().isoformat()
            )

        # -----------------------------------------------------
        # No explicit time, but previous target time should be
        # preserved.
        # -----------------------------------------------------

        elif (
            resolved.uses_previous_time
            and previous_target is not None
            and not is_current_weather
        ):

            target_local_time = (
                previous_target.isoformat()
            )

            target_date = (
                previous_target.date().isoformat()
            )

        # -----------------------------------------------------
        # Standalone day-level forecast.
        #
        # "What is the weather tomorrow?"
        # Keep target_local_time = None.
        # -----------------------------------------------------

        elif (
            has_explicit_day
            and not has_explicit_time
        ):

            target_local_time = None

        # -----------------------------------------------------
        # Persist resolved date/time context.
        # -----------------------------------------------------

        self.context = update_context(
            self.context,

            intent=intent,

            activity=(
                resolved.activity
                or self.context.activity
            ),

            target_local_time=
                target_local_time,

            target_date=
                target_date,

            timezone=
                active_timezone,

            latitude=
                active_latitude,

            longitude=
                active_longitude,

            language=
                detected_language,
        )

        # -----------------------------------------------------
        # -----------------------------------------------------
        # Run the appropriate weather tool.
        # -----------------------------------------------------

        # Persist the resolved conversational state before
        # selecting and executing the weather tool.
        self.context = update_context(
            self.context,

            intent=intent,

            activity=(
                resolved.activity
                or self.context.activity
            ),

            target_local_time=
                target_local_time,

            timezone=
                active_timezone,

            latitude=
                active_latitude,

            longitude=
                active_longitude,

            language=
                detected_language,
        )

        # Convert the resolved ISO timestamp into a datetime
        # object for time-specific tools.
        target_datetime = None

        if target_local_time:

            try:
                target_datetime = datetime.fromisoformat(
                    target_local_time
                )

            except ValueError:
                target_datetime = None

        tool_result = await run_weather_tool(
            intent=self.context.intent
                or "CURRENT_WEATHER",

            latitude=active_latitude,
            longitude=active_longitude,

            target_local_time=
                target_datetime,

            activity=
                self.context.activity,

            best_time_request=
                best_time_request,

            date_text=(
                resolved.day_text
                or "tomorrow"
            ),

            timezone_name=
                active_timezone,
        )

        # -----------------------------------------------------
        # Build grounded context for Sarvam.
        # -----------------------------------------------------

        weather_context = {
            "agent": {
                "intent":
                    self.context.intent,

                "activity":
                    self.context.activity,

                "target_local_time":
                    self.context.target_local_time,

                "timezone":
                    self.context.timezone,
            },

            "tool_result":
                tool_result,

            "location": {
                "latitude":
                    latitude,

                "longitude":
                    longitude,
            },
        }

        import json

        weather_text = json.dumps(
            weather_context,
            indent=2,
            ensure_ascii=False,
            default=str,
        )

        # -----------------------------------------------------
        # Generate final grounded answer.
        # -----------------------------------------------------

        answer = await sarvam_chat(
            question=question,
            language=detected_language
                or "English",
            weather_context=weather_text,
            history=history,
        )

        return {
            "status":
                "success",

            "type":
                "WEATHER_AGENT",

            "question":
                question,

            "intent":
                self.context.intent,

            "activity":
                self.context.activity,

            "target_local_time":
                self.context.target_local_time,

            "language":
                detected_language,

            "language_code":
                language_code,

            "script_code":
                script_code,

            "answer":
                answer,

            "tool":
                tool_result,

            "context":
                asdict(
                    self.context
                ),

            "location_used": {
                "name":
                    (
                        requested_location.get(
                            "name"
                        )
                        if requested_location
                        else None
                    ),

                "latitude":
                    active_latitude,

                "longitude":
                    active_longitude,

                "timezone":
                    active_timezone,

                "country":
                    (
                        requested_location.get(
                            "country"
                        )
                        if requested_location
                        else None
                    ),

                "admin1":
                    (
                        requested_location.get(
                            "admin1"
                        )
                        if requested_location
                        else None
                    ),
            },

            "agent_state":
                asdict(
                    self.context
                ),
        }
