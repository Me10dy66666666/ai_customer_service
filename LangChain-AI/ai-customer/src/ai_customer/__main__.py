# from __future__ import annotations

# from ai_customer.cli import app

# if __name__ == "__main__":
#     app()




# main.py
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

# 导入我们之前写好的路由聚合器（在 api/routes/__init__.py 中）
from ai_customer.api.routes import router as api_router

# ==================== 1. 配置日志 ====================
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ==================== 2. 生命周期管理（启动/关闭钩子）====================
@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    这个函数在服务启动时执行一次，关闭时执行一次。
    相当于给 Agent 做“开机自检”和“关机清理”。
    """
    # --- 启动时执行 ---
    logger.info("🚀 AI 客服 Agent 服务正在启动...")
    # 如果你做了“全局预编译工作流”的优化，可以在这里加载进内存
    # from ai_customer.workflows.support_workflow import build_support_workflow
    # app.state.workflow = build_support_workflow()  
    logger.info("✅ 服务启动完成，等待请求...")
    
    yield  # 这行代码把服务“挂起”，直到收到关闭信号
    
    # --- 关闭时执行 ---
    logger.info("🛑 AI 客服 Agent 服务正在关闭，释放资源...")

# ==================== 3. 创建 FastAPI 实例 ====================
app = FastAPI(
    title="AI 智能客服助手",
    description="基于 LangGraph 的多智能体客服系统",
    version="1.0.0",
    lifespan=lifespan,  # 注册生命周期钩子
    docs_url="/docs",   # Swagger 文档地址
    redoc_url="/redoc", # ReDoc 文档地址
)

# ==================== 4. 配置跨域（CORS）====================
# 【关键】如果不加这个，前端（比如 localhost:3000）访问你的 API 会被浏览器拦截
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境请改为具体域名，如 ["https://your-frontend.com"]
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ==================== 5. 挂载路由 ====================
# 将我们之前写的所有接口（/agent/chat）挂载到全局前缀 /api/v1 下
# 最终前端请求地址：http://localhost:8000/api/v1/agent/chat
app.include_router(api_router, prefix="/api/v1")

# ==================== 6. 全局健康检查（给运维或负载均衡器用）====================
@app.get("/health", tags=["系统"])
async def health_check():
    """
    心跳检测接口，返回服务是否存活。
    运维系统（如 K8s）会定时访问这个接口确认服务没挂。
    """
    return JSONResponse(status_code=200, content={"status": "alive", "service": "ai-agent"})

@app.get("/", tags=["系统"])
async def root():
    """
    根路径欢迎语。
    """
    return {"message": "欢迎使用 AI 智能客服助手 API", "docs": "/docs"}