from __future__ import annotations

from langgraph.graph import END, START, StateGraph

from ai_customer.agents.support_agent import run_support_agent
from ai_customer.schemas.state import SupportWorkflowState
from ai_customer.tools.builtin import classify_support_request


def classify_node(state: SupportWorkflowState) -> SupportWorkflowState:
    """Classify the incoming message for downstream orchestration."""

    category = classify_support_request.invoke({"user_message": state["user_input"]})
    return {**state, "category": category}


def agent_node(state: SupportWorkflowState) -> SupportWorkflowState:
    """Delegate the response generation to the support agent."""

    agent_response = run_support_agent(state["user_input"])
    return {**state, "agent_response": agent_response}


def finalize_node(state: SupportWorkflowState) -> SupportWorkflowState:
    """Format the final workflow output for the caller."""

    category = state.get("category", "unknown")
    agent_response = state.get("agent_response", "")
    final_response = f"[分类: {category}]\n{agent_response}"
    return {**state, "final_response": final_response}


def build_support_workflow():
    """Compile the state graph for the customer support workflow."""

    graph_builder = StateGraph(SupportWorkflowState)
    graph_builder.add_node("classify", classify_node)
    graph_builder.add_node("agent", agent_node)
    graph_builder.add_node("finalize", finalize_node)
    graph_builder.add_edge(START, "classify")
    graph_builder.add_edge("classify", "agent")
    graph_builder.add_edge("agent", "finalize")
    graph_builder.add_edge("finalize", END)
    return graph_builder.compile()


def run_support_workflow(user_input: str) -> str:
    """Run the compiled workflow and return the final response."""

    workflow = build_support_workflow()
    final_state = workflow.invoke({"user_input": user_input})
    return str(final_state.get("final_response", ""))
