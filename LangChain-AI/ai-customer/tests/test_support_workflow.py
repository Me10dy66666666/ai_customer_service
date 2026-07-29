from ai_customer.workflows import support_workflow


def test_run_support_workflow_formats_result(monkeypatch) -> None:
    def fake_agent_handler(user_input: str) -> str:
        return f"已处理: {user_input}"

    monkeypatch.setattr(support_workflow, "run_support_agent", fake_agent_handler)

    response = support_workflow.run_support_workflow("登录报错怎么办")

    assert "[分类: technical_support]" in response
    assert "已处理: 登录报错怎么办" in response
