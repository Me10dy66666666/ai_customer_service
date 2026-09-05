from ai_customer.prompts.system import (
    CUSTOMER_SUPPORT_SYSTEM_PROMPT,
    build_customer_context,
)


def test_system_prompt_is_static_and_does_not_embed_customer_data():
    assert "{user_type}" not in CUSTOMER_SUPPORT_SYSTEM_PROMPT
    assert "PIPELINE_SERVICE_TOKEN" not in CUSTOMER_SUPPORT_SYSTEM_PROMPT


def test_runtime_context_is_bounded():
    context = build_customer_context(1, "历史" * 2_000, "知识" * 10_000)
    assert len(context) < 6_500
    assert "用户类型：会员用户" in context
