# test_db.py
import sys
from pathlib import Path

# 确保项目 src 目录在 sys.path 中，无论从哪个目录运行都能正确导入
_src_path = Path(__file__).resolve().parent.parent / "src"
if str(_src_path) not in sys.path:
    sys.path.insert(0, str(_src_path))

from ai_customer.core.base.mysqlConnector import get_engine
from sqlalchemy import text

if __name__ == "__main__":
    try:
        print("[INFO] Trying to connect to database...")
        engine = get_engine()

        # 执行一次真正的 Ping（SELECT 1）
        with engine.connect() as conn:
            result = conn.execute(text("SELECT 1")).scalar()
            print(f"[OK] Database connected! Ping result: {result}")

        # 确认当前连接的数据库
        with engine.connect() as conn:
            db_name = conn.execute(text("SELECT DATABASE()")).scalar()
            print(f"[OK] Current database: {db_name}")

    except Exception as e:
        print(f"[FAIL] Connection failed: {e}")
