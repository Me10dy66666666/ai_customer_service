from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from urllib.parse import quote_plus

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


_ENV_FILE = Path(__file__).resolve().parents[3] / ".env"


class VectorSettings(BaseSettings):
    """Configuration for the shared PostgreSQL/pgvector data-pipeline service."""

    base_url: str = Field(default="http://localhost:3002", alias="DATA_PIPELINE_URL")
    service_token: str = Field(default="", alias="PIPELINE_SERVICE_TOKEN")
    dataset_id: str = Field(default="default", alias="VECTOR_DATASET_ID")
    timeout_ms: int = Field(default=5_000, alias="VECTOR_TIMEOUT_MS")
    retry_attempts: int = Field(default=2, alias="VECTOR_RETRY_ATTEMPTS")
    retry_delay_ms: int = Field(default=200, alias="VECTOR_RETRY_DELAY_MS")

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


class DatabaseSettings(BaseSettings):
    """MySQL connection-pool settings for the legacy ingestion command."""

    host: str = Field(default="localhost", alias="MYSQL_HOST")
    port: int = Field(default=3306, alias="MYSQL_PORT")
    user: str = Field(default="root", alias="MYSQL_USER")
    password: str = Field(default="", alias="MYSQL_PASS")
    db_name: str = Field(default="ai_customer_service", alias="MYSQL_DB")
    pool_size: int = Field(default=10, alias="MYSQL_POOL_SIZE")
    max_overflow: int = Field(default=20, alias="MYSQL_MAX_OVERFLOW")
    pool_recycle: int = Field(default=3600, alias="MYSQL_POOL_RECYCLE")

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def url(self) -> str:
        """Build the SQLAlchemy URL without exposing credentials in logs."""

        return (
            f"mysql+pymysql://{self.user}:{quote_plus(self.password)}@"
            f"{self.host}:{self.port}/{self.db_name}?charset=utf8mb4"
        )


class AppSettings(BaseSettings):
    """Application settings loaded from environment variables."""

    app_env: str = Field(default="development", alias="APP_ENV")
    log_level: str = Field(default="INFO", alias="LOG_LEVEL")
    openai_api_key: str = Field(default="", alias="OPENAI_API_KEY")
    openai_base_url: str = Field(default="https://api.openai.com/v1", alias="OPENAI_BASE_URL")
    openai_model: str = Field(default="Deepseek-V4-Flash", alias="OPENAI_MODEL")
    openai_temperature: float = Field(default=0.2, alias="OPENAI_TEMPERATURE")
    database: DatabaseSettings = Field(default_factory=DatabaseSettings)
    vector: VectorSettings = Field(default_factory=VectorSettings)

    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )


@lru_cache(maxsize=1)
def get_settings() -> AppSettings:
    """Return one validated settings instance for the process."""

    return AppSettings()


settings = get_settings()
