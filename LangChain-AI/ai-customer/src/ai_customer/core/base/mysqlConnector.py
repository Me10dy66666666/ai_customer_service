# core/base/mysql_connector.py
from sqlalchemy import create_engine, text
from sqlalchemy.pool import QueuePool
from ai_customer.config.settings import settings  # 导入全局实例
import logging

logger = logging.getLogger(__name__)
_engine = None

def get_engine():
    global _engine
    if _engine is None:
        db = settings.database  # 🚀 清晰的结构化访问
        _engine = create_engine(
            db.url,  # 使用 property
            poolclass=QueuePool,
            pool_size=db.pool_size,
            max_overflow=db.max_overflow,
            pool_recycle=db.pool_recycle,
            pool_pre_ping=True,
            pool_timeout=30,
        )
        logger.info(f"MySQL 连接池初始化成功: {db.host}:{db.port}/{db.db_name}")
    return _engine
