from __future__ import annotations

from langchain_openai import ChatOpenAI

from ai_customer.config.settings import AppSettings


def build_chat_model(settings: AppSettings) -> ChatOpenAI:
    """Build the default chat model used by agents and workflows."""

    if not settings.openai_api_key:
        raise ValueError("缺少 OPENAI_API_KEY，请先在 .env 中配置模型密钥。")

    return ChatOpenAI(
        model=settings.openai_model,
        api_key=settings.openai_api_key,
        base_url=settings.openai_base_url,
        temperature=settings.openai_temperature,
    )
