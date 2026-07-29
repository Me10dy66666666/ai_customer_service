from ai_customer.tools.builtin import classify_support_request, get_beijing_time


def test_get_beijing_time_returns_string() -> None:
    output = get_beijing_time.invoke({})
    assert isinstance(output, str)
    assert output


def test_classify_support_request_for_billing() -> None:
    category = classify_support_request.invoke({"user_message": "我的订单想退款"})
    assert category == "billing"
