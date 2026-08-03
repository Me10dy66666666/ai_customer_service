from __future__ import annotations

from langchain.agents import create_agent

from ai_customer.config.settings import get_settings
from ai_customer.llms.factory import build_chat_model
from ai_customer.prompts.system import CUSTOMER_SUPPORT_SYSTEM_PROMPT
from ai_customer.tools.builtin import get_builtin_tools


def build_support_agent():
    """Create a tool-calling agent backed by LangChain (langchain 1.x create_agent)."""

    settings = get_settings()
    model = build_chat_model(settings)
    tools = get_builtin_tools()
    return create_agent(
        model=model,
        tools=tools,
        system_prompt=CUSTOMER_SUPPORT_SYSTEM_PROMPT,
    )


def run_support_agent(user_input: str) -> str:
    """Run the support agent and return the final assistant message."""

    agent = build_support_agent()
    response = agent.invoke({"messages": [{"role": "user", "content": user_input}]})
    messages = response.get("messages", [])
    if messages:
        return str(messages[-1].content)
    return "智能体未返回任何消息。"
