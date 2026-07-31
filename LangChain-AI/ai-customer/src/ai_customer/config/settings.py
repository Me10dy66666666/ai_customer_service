from __future__ import annotations

from functools import lru_cache
from pathlib import Path
from urllib.parse import quote_plus

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import Optional

# .env 文件相对于项目根目录（settings.py 向上两级：config -> ai_customer -> src -> 项目根）
_ENV_FILE = Path(__file__).resolve().parent.parent.parent.parent / ".env"
class VectorSettings:
    # 向量库业务配置（这里写你实际使用的值）
    PERSIST_PATH = "./chroma_db"
    COLLECTION_NAME = "ai_customer"
    EMBEDDING_MODEL = "qwen3.7-text-embedding"  # 注意：这里保持和你入库时一致！
    
    # 检索配置
    RETRIEVAL_K = 5  # 每次召回几个块
    SIMILARITY_THRESHOLD = 0.7  # 相似度阈值（可选）
    
    


class DatabaseSettings(BaseSettings):
    """数据库连接池配置，自动从 .env 读取 MYSQL_* 变量"""
    host: str = Field(default="localhost", alias="MYSQL_HOST")
    port: int = Field(default=3306, alias="MYSQL_PORT")
    user: str = Field(default="root", alias="MYSQL_USER")
    password: str = Field(default="123456", alias="MYSQL_PASS")
    db_name: str = Field(default="ai_customer_service", alias="MYSQL_DB")
    
    # 连接池参数（也建议放环境变量，方便调优）
    pool_size: int = Field(default=10, alias="MYSQL_POOL_SIZE")
    max_overflow: int = Field(default=20, alias="MYSQL_MAX_OVERFLOW")
    pool_recycle: int = Field(default=3600, alias="MYSQL_POOL_RECYCLE")
    
    # 只加载以 MYSQL_ 为前缀的变量，避免与 AppSettings 的字段冲突
    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )
    
    @property
    def url(self) -> str:
        """自动构建 SQLAlchemy 连接串，方便 core/base/mysql_connector.py 直接调用"""
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

    # 🚀 关键：将数据库配置作为子属性嵌套进来
    database: DatabaseSettings = Field(default_factory=DatabaseSettings)

     # 🚀 添加向量库配置
    vector: VectorSettings = Field(default_factory=VectorSettings)



    model_config = SettingsConfigDict(
        env_file=_ENV_FILE,
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )



@lru_cache(maxsize=1)
def get_settings() -> AppSettings:
    """Return a cached settings instance."""

    return AppSettings()

# ========== 3. 全局单例实例 ==========
settings = AppSettings()
