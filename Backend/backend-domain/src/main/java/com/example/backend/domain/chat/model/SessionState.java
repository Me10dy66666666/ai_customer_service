package com.example.backend.domain.chat.model;

/**
 * 会话状态枚举 — 控制 MessageRouter 的消息分发逻辑。
 */
public enum SessionState {
    /** AI 正与用户对话 */
    AI,
    /** 用户已请求人工，等待客服接入 */
    WAITING,
    /** 客服已接入，正在进行人工对话 */
    HUMAN,
    /** 会话已关闭 */
    CLOSED
}
