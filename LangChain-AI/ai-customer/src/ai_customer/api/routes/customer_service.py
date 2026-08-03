# api/routes/chat.py
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel
from ai_customer.workflows.support_workflow import run_support_workflow


router = APIRouter()

# --- 定义请求和响应的数据模型（DTO） ---
class ChatRequest(BaseModel):
    user_input: str
    history: list[str] = []
    userType: int

class ChatResponse(BaseModel):
    reply: str
    # 注意：这里不包含 category, context, retrieved_docs 等内部字段

# --- 👇 封装的具体位置：就是这个路由函数 ---
@router.post("/customerService", response_model=ChatResponse)
async def chat_endpoint(request: ChatRequest):
    # 1. 调用工作流，拿到最终回复
    # run_support_workflow 内部已完成「分类 + 智能体回复」的拼接，直接返回字符串
    reply_content = run_support_workflow(request.user_input)

    # 2. 【封装动作】：构造标准的 HTTP 响应返回
    # FastAPI 会自动将 ChatResponse 序列化为 JSON
    return ChatResponse(reply=reply_content)