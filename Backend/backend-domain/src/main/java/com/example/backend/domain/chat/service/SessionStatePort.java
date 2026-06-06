package com.example.backend.domain.chat.service;

import com.example.backend.domain.chat.model.SessionState;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 会话状态存储抽象端口 — Redis Hash 实现，会话结束后 MQ 异步落盘 MySQL。
 */
public interface SessionStatePort {

    SessionState getState(String sessionId);

    void setState(String sessionId, SessionState state, Long agentId);

    Long getAgentId(String sessionId);

    SessionInfo getSessionInfo(String sessionId);

    /** 判断会话是否处于 WAITING 等待队列中 */
    boolean isWaiting(String sessionId);

    /** 设置会话的用户信息（userId, intent） */
    void setUserInfo(String sessionId, Long userId, String intent);

    // ============ 派发绑定 ============

    /** 记录会话被派发给哪个客服 */
    void setSessionDispatched(String sessionId, Long agentId);

    /** 查询会话被派发给哪个客服，null 表示未派发 */
    Long getSessionDispatched(String sessionId);

    /** 清除会话的派发绑定 */
    void clearSessionDispatched(String sessionId);

    // ============ 分布式锁 ============

    /**
     * 原子锁定会话，用于防止并发重复分配。
     * @return true=获取锁成功, false=已被其他客服锁定
     */
    boolean acquireClaimLock(String sessionId, Long agentId);

    /** 释放会话的认领锁 */
    void releaseClaimLock(String sessionId);

    // ============ 等待队列 (FIFO) ============

    /** 将会话加入 FIFO 等待队列（按时间戳排序的 ZSet） */
    void addToWaitQueue(String sessionId);

    /** 从等待队列移除 */
    void removeFromWaitQueue(String sessionId);

    /** 获取等待队列中第N个最早进入的会话 */
    String peekOldestWaiting();

    /** 获取等待队列大小 */
    long getWaitQueueSize();

    /** 获取指定会话在队列中的位置（从1开始，0表示不在队列中） */
    long getWaitQueuePosition(String sessionId);

    /** 获取预估等候时间（秒），基于队列位置和平均处理时间 */
    long getEstimatedWaitTime(String sessionId);

    /** 获取全部等待中的会话详情 */
    List<Map<String, Object>> getAllWaitingSessionDetails();

    /** 更新平均处理时间（用于预估等候时间） */
    void recordAverageHandleTime(long avgSeconds);

    /** 获取平均处理时间 */
    long getAverageHandleTime();

    // ============ 心跳与在线状态 ============

    /** 标记客服上线 */
    void markAgentOnline(Long agentId);

    /** 标记客服下线 */
    void markAgentOffline(Long agentId);

    /** 刷新客服心跳时间 */
    void refreshAgentHeartbeat(Long agentId);

    /** 客服是否在线 */
    boolean isAgentOnline(Long agentId);

    /** 获取所有在线客服ID */
    Set<Long> getOnlineAgents();

    /** 获取客服的所有活跃会话 */
    List<String> getAgentActiveSessions(Long agentId);

    /** 客服认领会话时记录到客服的活跃会话列表 */
    void addAgentActiveSession(Long agentId, String sessionId);

    /** 客服释放会话时从活跃会话列表移除 */
    void removeAgentActiveSession(Long agentId, String sessionId);

    /** 清除过期心跳的客服（超过阈值秒未心跳）并返回离线客服列表 */
    Set<Long> expireStaleHeartbeats(long timeoutSeconds);

    // ============ AI 阻断开关 ============

    /** 设置 AI 阻断标记：转人工成功后设为 true，会话结束/转回 AI 后设为 false */
    void setAiBlocked(String sessionId, boolean blocked);

    /** 查询 AI 是否被阻断 */
    boolean isAiBlocked(String sessionId);

    record SessionInfo(String sessionId, SessionState status, Long userId, Long agentId, String intent) {}
}
