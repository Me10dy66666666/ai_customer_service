from __future__ import annotations

from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain_core.prompts import ChatPromptTemplate

from ai_customer.config.settings import get_settings
from ai_customer.llms.factory import build_chat_model
from ai_customer.prompts.system import CUSTOMER_SYSTEM_PROMPT
from ai_customer.tools import get_knowledge_base_tools


def build_customer_agent() -> AgentExecutor:
    """Create a tool-calling agent backed by LangChain."""

    settings = get_settings()
    model = build_chat_model(settings)
    tools = get_knowledge_base_tools()
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", CUSTOMER_SYSTEM_PROMPT),
            ("human", "{input}"),
            ("placeholder", "{agent_scratchpad}"),
        ]
    )
    agent = create_tool_calling_agent(model, tools, prompt)
    return AgentExecutor(agent=agent, tools=tools, verbose=False)


def run_customer_agent(user_input: str, his_ords: str, user_type: int, context: str) -> str:
    """Run the customer agent and return the final assistant message."""

    agent = build_customer_agent()
    response = agent.invoke({"input": user_input},{"history_orders":his_ords},{"user_type":user_type},{"context":context})
    return str(response.get("output", "智能体未返回任何消息。"))