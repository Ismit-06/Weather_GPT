from dataclasses import dataclass


@dataclass
class ConversationContext:
    intent: str | None = None
    activity: str | None = None
    target_local_time: str | None = None
    timezone: str | None = None


def update_context(
    previous: ConversationContext,
    *,
    intent: str | None = None,
    activity: str | None = None,
    target_local_time: str | None = None,
    timezone: str | None = None,
) -> ConversationContext:

    return ConversationContext(
        intent=intent or previous.intent,
        activity=activity or previous.activity,
        target_local_time=(
            target_local_time
            or previous.target_local_time
        ),
        timezone=(
            timezone
            or previous.timezone
        ),
    )
