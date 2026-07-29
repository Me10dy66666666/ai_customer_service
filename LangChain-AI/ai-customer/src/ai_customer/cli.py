from __future__ import annotations

import typer
from rich import print

from ai_customer.agents.support_agent import run_support_agent
from ai_customer.config.settings import get_settings
from ai_customer.core.logging import configure_logging
from ai_customer.workflows.support_workflow import run_support_workflow

app = typer.Typer(help="AI customer service starter based on LangChain and LangGraph.")


@app.callback()
def main() -> None:
    """Initialize shared runtime dependencies."""

    settings = get_settings()
    configure_logging(settings.log_level)


@app.command("agent")
def agent_command(user_input: str) -> None:
    """Run the support agent directly."""

    print(run_support_agent(user_input))


@app.command("workflow")
def workflow_command(user_input: str) -> None:
    """Run the stateful workflow around the support agent."""

    print(run_support_workflow(user_input))
