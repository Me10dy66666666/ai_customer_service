from __future__ import annotations

from langgraph.graph import END, START, StateGraph

from ai_customer.agents.customer_agent import run_customer_agent
from ai_customer.schemas.state import CustomerServiceWorkflowState
from ai_customer.tools.vector_search_tool import get_knowledge_base_tools

def search_node(state: CustomerServiceWorkflowState) -> CustomerServiceWorkflowState:
    """Search the knowledge base for the user's query."""
    tools = get_knowledge_base_tools()
    query_result = tools[0].invoke({"query": state["user_input"]})
    return {**state, "context": query_result}

def agent_node(state: CustomerServiceWorkflowState) -> CustomerServiceWorkflowState:
    """Run the customer agent and return the final assistant message."""
    response = run_customer_agent(state["user_input"], state["history_orders"], state["userType"], state["context"])
    return {**state, "agent_response": response}

def build_customer_service_workflow() -> StateGraph:
    """Compile the state graph for the customer service workflow."""
    graph_builder = StateGraph(CustomerServiceWorkflowState)
    graph_builder.add_node(search_node)
    graph_builder.add_node(agent_node)
    graph_builder.add_edge(START, search_node)
    graph_builder.add_edge(search_node, agent_node)
    graph_builder.add_edge(agent_node, END)
    return graph_builder.compile()

def run_customer_service_workflow(user_input: str, history_orders: str, userType: int) -> str:
    """Run the customer service workflow."""
    workflow = build_customer_service_workflow()
    state = workflow.run(
        user_input=user_input,
        history_orders=history_orders,
        userType=userType
    )
    return state["agent_response"]