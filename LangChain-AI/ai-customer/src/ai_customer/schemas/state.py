from __future__ import annotations

from typing import NotRequired, TypedDict


class SupportWorkflowState(TypedDict):
    """State shared across the LangGraph customer support workflow."""

    userType: int
    user_input: str
    history_Order: NotRequired[str]
    agent_response: NotRequired[str]
    final_response: NotRequired[str]
