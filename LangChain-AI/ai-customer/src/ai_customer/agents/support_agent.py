from __future__ import annotations

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate

from ai_customer.config.settings import get_settings
from ai_customer.llms.factory import build_chat_model
from ai_customer.prompts.system import CUSTOMER_SUPPORT_SYSTEM_PROMPT
from ai_customer.tools import get_builtin_tools


def build_support_agent() -> AgentExecutor:
    """Create a tool-calling agent backed by LangChain."""

    settings = get_settings()
    model = build_chat_model(settings)
    tools = get_builtin_tools()
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", CUSTOMER_SUPPORT_SYSTEM_PROMPT),
            ("human", "{input}"),
            ("placeholder", "{agent_scratchpad}"),
        ]
    )
    agent = create_tool_calling_agent(model, tools, prompt)
    return AgentExecutor(agent=agent, tools=tools, verbose=False)


def run_support_agent(user_input: str) -> str:
    """Run the support agent and return the final assistant message."""

    agent = build_support_agent()
    response = agent.invoke({"input": user_input})
    return str(response.get("output", "智能体未返回任何消息。"))
