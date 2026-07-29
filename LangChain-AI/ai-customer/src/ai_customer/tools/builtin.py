from __future__ import annotations

from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from langchain_core.tools import tool


@tool
def get_beijing_time() -> str:
    """Return the current Beijing time in a readable format."""

    try:
        beijing_timezone = ZoneInfo("Asia/Shanghai")
    except ZoneInfoNotFoundError:
        beijing_timezone = timezone(timedelta(hours=8), name="UTC+8")

    current_time = datetime.now(tz=beijing_timezone)
    return current_time.strftime("%Y-%m-%d %H:%M:%S %Z")


@tool
def classify_support_request(user_message: str) -> str:
    """Classify a support request into a simple business category."""

    normalized_message = user_message.lower()
    if any(keyword in normalized_message for keyword in ["退款", "退费", "refund"]):
        return "billing"
    if any(keyword in normalized_message for keyword in ["报错", "错误", "异常", "error"]):
        return "technical_support"
    if any(keyword in normalized_message for keyword in ["账号", "登录", "密码", "account"]):
        return "account"
    return "general_inquiry"


def get_builtin_tools() -> list:
    """Return the built-in tool set for the agent runtime."""

    return [get_beijing_time, classify_support_request]
