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
import re


def resolve_language(question: str, requested_lang: str | None) -> tuple[str, str, str | None]:
    """
    Returns (detected_language, language_code, script_code)
    Handles Auto-detection (Script-based + keywords + fallback) as well as explicitly chosen languages.
    """
    # If user explicitly selected a language from the UI:
    if requested_lang and requested_lang.strip().lower() != "auto":
        req = requested_lang.strip().lower()
        if "odia" in req or "oriya" in req or req == "or" or req == "od-in" or req == "or-in":
            return ("Odia", "or-IN", "Orya")
        if "hinglish" in req:
            return ("Hinglish", "hi-IN", "Latn")
        if "hindi" in req or req == "hi" or req == "hi-in":
            return ("Hindi", "hi-IN", "Deva")
        if "telugu" in req or req == "te" or req == "te-in":
            return ("Telugu", "te-IN", "Telu")
        if "tamil" in req or req == "ta" or req == "ta-in":
            return ("Tamil", "ta-IN", "Taml")
        if "bengali" in req or req == "bn" or req == "bn-in":
            return ("Bengali", "bn-IN", "Beng")
        if "marathi" in req or req == "mr" or req == "mr-in":
            return ("Marathi", "mr-IN", "Deva")
        if "gujarati" in req or req == "gu" or req == "gu-in":
            return ("Gujarati", "gu-IN", "Gujr")
        if "kannada" in req or req == "kn" or req == "kn-in":
            return ("Kannada", "kn-IN", "Knda")
        if "malayalam" in req or req == "ml" or req == "ml-in":
            return ("Malayalam", "ml-IN", "Mlym")
        if "punjabi" in req or req == "pa" or req == "pa-in":
            return ("Punjabi", "pa-IN", "Guru")
        if "english" in req or req == "en" or req == "en-in":
            return ("English", "en-IN", "Latn")
        return (requested_lang.capitalize(), "en-IN", None)

    # AUTO-DETECTION:
    # 1. Check Unicode script in question
    if re.search(r'[\u0B00-\u0B7F]', question):
        return ("Odia", "or-IN", "Orya")
    if re.search(r'[\u0C00-\u0C7F]', question):
        return ("Telugu", "te-IN", "Telu")
    if re.search(r'[\u0B80-\u0BFF]', question):
        return ("Tamil", "ta-IN", "Taml")
    if re.search(r'[\u0980-\u09FF]', question):
        return ("Bengali", "bn-IN", "Beng")
    if re.search(r'[\u0C80-\u0CFF]', question):
        return ("Kannada", "kn-IN", "Knda")
    if re.search(r'[\u0D00-\u0D7F]', question):
        return ("Malayalam", "ml-IN", "Mlym")
    if re.search(r'[\u0A80-\u0AFF]', question):
        return ("Gujarati", "gu-IN", "Gujr")
    if re.search(r'[\u0A00-\u0A7F]', question):
        return ("Punjabi", "pa-IN", "Guru")
    if re.search(r'[\u0900-\u097F]', question):
        return ("Hindi", "hi-IN", "Deva")

    # 2. Check Romanized keywords
    lower_q = question.lower()
    odia_keywords = ["barsa", "barsha", "aaji", "kete", "kemiti", "nahin", "heba", "pani", "tati", "bhitare", "kahinki", "bhubaneswar", "cuttack", "odisha"]
    if any(re.search(r'\b' + re.escape(w) + r'\b', lower_q) for w in odia_keywords):
        return ("Odia", "or-IN", "Latn")

    hindi_keywords = ["kya", "aaj", "barish", "baarish", "hogi", "hoga", "mausam", "kaisa", "batao", "hai", "hain", "garmi", "thand", "dhoop", "kal", "parso"]
    if any(re.search(r'\b' + re.escape(w) + r'\b', lower_q) for w in hindi_keywords):
        return ("Hinglish", "hi-IN", "Latn")

    # Default fallback to English
    return ("English", "en-IN", "Latn")


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

        detected_language, language_code, script_code = resolve_language(
            question=question,
            requested_lang=language,
        )

        if (not language or language.lower() == "auto") and detected_language == "English":
            try:
                detection = await detect_language(question)
                code = detection.get("language_code")
                if code and code != "en-IN":
                    language_code = code
                    detected_language = detection.get("language", "English")
                    script_code = detection.get("script_code")
            except Exception:
                pass

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

        # -----------------------------------------------------
        # Fallback: Auto-reverse geocode coordinates if location name is missing
        # -----------------------------------------------------
        if requested_location is None or not requested_location.get("name"):
            try:
                from app.services.reverse_geocoding import reverse_geocode
                geo = await reverse_geocode(active_latitude, active_longitude)
                requested_location = {
                    "name": geo.get("name") or "Current Location",
                    "latitude": active_latitude,
                    "longitude": active_longitude,
                    "country": geo.get("country"),
                    "admin1": geo.get("state"),
                    "timezone": active_timezone,
                }
            except Exception:
                requested_location = {
                    "name": "Current Location",
                    "latitude": active_latitude,
                    "longitude": active_longitude,
                    "timezone": active_timezone,
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

        tool_result = None

        # -----------------------------------------------------
        # Best-time tool
        # -----------------------------------------------------

        if best_time_request:

            activity = (
                resolved.activity
                or self.context.activity
                or "outdoor"
            )

            date_text = (
                resolved.day_text
                or "today"
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

            tool_result = result
            # Proceed directly down to LLM generation below rather than early-exiting with raw data

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

        if tool_result is None:
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

        # -----------------------------------------------------
        # Build clean summary for LLMs to prevent over-analyzing raw JSON
        # -----------------------------------------------------
        loc_name = requested_location.get("name") if requested_location else None
        summary_lines = []
        if loc_name:
            summary_lines.append(f"Location: {loc_name}")
        if self.context.intent:
            summary_lines.append(f"User Intent: {self.context.intent}")
        if self.context.activity:
            summary_lines.append(f"User Activity: {self.context.activity}")
        if self.context.target_local_time:
            summary_lines.append(f"Target Time: {self.context.target_local_time}")

        if isinstance(tool_result, dict):
            if "timeline" in tool_result and isinstance(tool_result["timeline"], list) and tool_result["timeline"]:
                summary_lines.append("Rain Timeline:")
                for tl in tool_result["timeline"]:
                    summary_lines.append(tl)

            if "rain_windows" in tool_result or "windows" in tool_result:
                windows = tool_result.get("rain_windows") or tool_result.get("windows", [])
                if windows:
                    summary_lines.append("Rain Windows:")
                    for w in windows:
                        if isinstance(w, dict):
                            s = w.get("start_label", w.get("start"))
                            e = w.get("end_label", w.get("end"))
                            summary_lines.append(f"- {s} to {e} ({w.get('intensity', 'MODERATE')} rain, total {w.get('total_rainfall_mm')} mm, peak {w.get('peak_hourly_rainfall_mm')} mm)")
                        else:
                            summary_lines.append(f"- {w}")
                    if tool_result.get("peak_time"):
                        summary_lines.append(f"Rain Peak Time: around {tool_result.get('peak_time')}")
                else:
                    summary_lines.append("Rain Windows: No rain detected in the forecast period.")
            if "recommendation" in tool_result:
                rec = tool_result["recommendation"]
                if isinstance(rec, dict):
                    summary_lines.append(
                        f"BEST WINDOW: ⭐ {rec.get('start')} – {rec.get('end')} (Score: {rec.get('score')}, Level: {rec.get('level')})"
                    )
                    w = rec.get("weather", {})
                    summary_lines.append(
                        f"Best Window Weather: {w.get('temperature_c')}°C, humidity {w.get('humidity_pct')}%, max rain {w.get('max_rainfall_mm')} mm, wind {w.get('wind_speed_ms')} m/s"
                    )
                    if rec.get("reasons"):
                        summary_lines.append("Conditions: " + "; ".join(rec.get("reasons", [])))
                else:
                    summary_lines.append(f"Recommendation: {rec}")

            if "other_windows" in tool_result and isinstance(tool_result["other_windows"], list):
                others = tool_result["other_windows"]
                alt_lines = []
                for idx, ow in enumerate(others[:3]):
                    try:
                        s_str = datetime.fromisoformat(ow["start"]).strftime("%I:%M %p").lstrip("0")
                        e_str = datetime.fromisoformat(ow["end"]).strftime("%I:%M %p").lstrip("0")
                        alt_lines.append(f"Alternative {idx+1}: {s_str} – {e_str} (Score: {ow.get('average_score')})")
                    except Exception:
                        pass
                if alt_lines:
                    summary_lines.append("ALTERNATIVE WINDOWS:\n" + "\n".join(alt_lines))

            if "activity_assessment" in tool_result:
                summary_lines.append(f"Activity Assessment: {tool_result['activity_assessment']}")
            if "assessment" in tool_result:
                summary_lines.append(f"Assessment: {tool_result['assessment']}")
            if "summary" in tool_result:
                summary_lines.append(f"Weather Summary: {tool_result['summary']}")
            if "explanation_context" in tool_result:
                ec = tool_result["explanation_context"]
                summary_lines.append(
                    f"Thermal Breakdown: Actual Temperature {ec.get('temperature_c')}°C, Relative Humidity {ec.get('humidity_pct')}%, Calculated Perceived Heat Index {ec.get('heat_index_c')}°C, Dew Point {ec.get('dew_point_c')}°C"
                )
            if "comfort_score" in tool_result:
                cs = tool_result["comfort_score"]
                bd = cs.get("breakdown", {})
                summary_lines.append(
                    f"Personal Comfort Score: {cs.get('score')} / 100 ({cs.get('emoji')} {cs.get('level_label')})\n"
                    f"Comfort Factors: Temperature {bd.get('temperature_c')}°C, Heat Index {bd.get('heat_index_c')}°C, Humidity {bd.get('humidity_pct')}%, Wind {bd.get('wind_speed_ms')} m/s, Rain {bd.get('rainfall_mm')} mm, UV {bd.get('uv_index')}, AQI {bd.get('aqi')}\n"
                    f"Clothing Advice: {cs.get('clothing_recommendation')}\n"
                    f"Outdoor Advice: {cs.get('outdoor_advice')}"
                )
            if "outfit_recommendation" in tool_result:
                outfit = tool_result["outfit_recommendation"]
                summary_lines.append(
                    f"AI Outfit Recommendation:\n"
                    f"👕 Wear: {outfit.get('wear')}\n"
                    f"🧥 Optional: {outfit.get('optional') or 'None'}\n"
                    f"☂️ Carry: {outfit.get('carry')}\n"
                    f"👟 Shoes: {outfit.get('shoes')}"
                )
            if "current" in tool_result and isinstance(tool_result["current"], dict):
                c = tool_result["current"]
                summary_lines.append(
                    f"Current: {c.get('temperature_c', c.get('temperature'))}°C, {c.get('condition', c.get('summary', 'clear'))}"
                )

        import json
        weather_summary_text = "\n".join(summary_lines)
        weather_text = f"{weather_summary_text}\n\nDATA:\n" + json.dumps(
            weather_context,
            indent=2,
            ensure_ascii=False,
            default=str,
        )

        # -----------------------------------------------------
        # Generate final grounded answer (OpenRouter / Sarvam / Fallback).
        # -----------------------------------------------------
        import os
        answer = None

        if os.getenv("OPENROUTER_API_KEY"):
            try:
                from app.services.openrouter_chat import chat as openrouter_chat
                answer = await openrouter_chat(
                    question=question,
                    language=detected_language or "English",
                    weather_context=weather_text,
                    history=history,
                )
            except Exception:
                answer = None

        if not answer and os.getenv("SARVAM_API_KEY"):
            try:
                answer = await sarvam_chat(
                    question=question,
                    language=detected_language or "English",
                    weather_context=weather_text,
                    history=history,
                )
            except Exception:
                answer = None

        if not answer:
            answer = self._generate_fallback_answer(
                question=question,
                weather_context=weather_context,
                tool_result=tool_result,
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

    def _generate_fallback_answer(
        self,
        question: str,
        weather_context: dict,
        tool_result: dict | None,
    ) -> str:
        import os
        if os.getenv("OPENAI_API_KEY"):
            try:
                from app.chat.llm_service import generate_weather_answer
                loc_str = f"{weather_context.get('location', {}).get('latitude', '')}, {weather_context.get('location', {}).get('longitude', '')}"
                return generate_weather_answer(
                    question=question,
                    location=loc_str,
                    weather_context=weather_context,
                )
            except Exception:
                pass

        if not tool_result:
            return "Based on your location, weather conditions are being monitored. Please specify your query."

        if isinstance(tool_result, dict):
            if tool_result.get("status") == "needs_clarification":
                activity = self.context.activity
                if activity:
                    return f"To evaluate conditions for {activity}, please specify a time (for example: 'Can I {activity} today at 4 PM?' or 'What about tomorrow?')."
                msg = tool_result.get("message")
                if msg:
                    return msg
                return "Please specify a specific time or date for your request."

            # If activity or conditions assessment was performed
            for key in ["answer", "recommendation", "assessment", "message", "summary"]:
                if key in tool_result and isinstance(tool_result[key], str) and tool_result[key].strip():
                    return tool_result[key].strip()

            current = tool_result.get("current") or tool_result.get("conditions")
            if isinstance(current, dict):
                temp = current.get("temperature_c") or current.get("temperature")
                rain = current.get("rain_probability_pct") or current.get("rainfall_probability")
                cond = current.get("condition") or current.get("summary")
                parts = []
                if temp is not None:
                    parts.append(f"The temperature is {temp}°C.")
                if rain is not None:
                    parts.append(f"Rain probability is {rain}%.")
                if cond:
                    parts.append(f"Condition: {cond}.")
                if parts:
                    return " ".join(parts)

        return "Weather conditions have been evaluated for your location. You can view the live parameters in the dashboard."

