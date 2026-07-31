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
    # 1. 调用工作流，拿到完整的原始 State（字典）
    final_state = run_support_workflow(request.user_input, request.history, request.userType)

    # 2. 【封装动作 1】：安全提取对外数据
    # 只取最终展示给用户的字段，决不允许 state 里的内部字段泄露出去
    reply_content = final_state.get("agent_response", "服务繁忙，请稍后再试。")
    

    # 4. 【封装动作 2】：构造标准的 HTTP 响应返回
    # FastAPI 会自动将 ChatResponse 序列化为 JSON
    return ChatResponse(reply=reply_content)