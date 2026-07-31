from fastapi import APIRouter
from .customer_service import router as customerService_router  # 从 chat.py 导入路由

# 创建一个总路由对象
router = APIRouter()

# 将各个子路由注册到总路由上
# 如果以后有 user.py、admin.py，都在这统一挂载
router.include_router(customerService_router, prefix="/agent", tags=["客服"])
# 假设未来有了 admin.py:
# router.include_router(admin_router, prefix="/admin", tags=["管理"])