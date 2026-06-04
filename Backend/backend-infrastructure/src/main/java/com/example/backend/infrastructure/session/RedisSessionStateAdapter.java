package com.example.backend.infrastructure.session;

import com.example.backend.domain.chat.model.SessionState;
import com.example.backend.domain.chat.service.SessionStatePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisSessionStateAdapter implements SessionStatePort {

    private final StringRedisTemplate redis;

    private static final String KEY_PREFIX = "session:";
    private static final String CLAIM_LOCK_PREFIX = "session:claim_lock:";
    private static final String WAIT_QUEUE_KEY = "ai_cs:wait_queue";
    private static final String AGENT_HEARTBEAT_PREFIX = "agent:heartbeat:";
    private static final String AGENT_SESSIONS_PREFIX = "agent:sessions:";
    private static final String AGENT_ONLINE_SET = "agent:online";
    private static final String AVG_HANDLE_TIME_KEY = "ai_cs:avg_handle_time";
    private static final Duration TTL = Duration.ofHours(6);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final long DEFAULT_AVG_HANDLE_SECONDS = 180;

    public RedisSessionStateAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public SessionState getState(String sessionId) {
        String v = (String) redis.opsForHash().get(KEY_PREFIX + sessionId, "status");
        if (v == null) return SessionState.AI;
        try { return SessionState.valueOf(v); }
        catch (IllegalArgumentException e) { return SessionState.AI; }
    }

    @Override
    public void setState(String sessionId, SessionState state, Long agentId) {
        String key = KEY_PREFIX + sessionId;
        Map<String, String> fields = new HashMap<>();
        fields.put("status", state.name());
        fields.put("agentId", agentId != null ? agentId.toString() : "");
        fields.put("lastActivityAt", String.valueOf(System.currentTimeMillis()));
        redis.opsForHash().putAll(key, fields);
        redis.expire(key, TTL);
        log.info("Session {} → {} (agent={})", sessionId, state, agentId);
    }

    @Override
    public Long getAgentId(String sessionId) {
        String v = (String) redis.opsForHash().get(KEY_PREFIX + sessionId, "agentId");
        if (v == null || v.isEmpty()) return null;
        try { return Long.parseLong(v); }
        catch (NumberFormatException e) { return null; }
    }

    @Override
    public SessionInfo getSessionInfo(String sessionId) {
        Map<Object, Object> fields = redis.opsForHash().entries(KEY_PREFIX + sessionId);
        return new SessionInfo(
                sessionId,
                parseState((String) fields.get("status")),
                parseLong((String) fields.get("userId")),
                parseLong((String) fields.get("agentId")),
                (String) fields.getOrDefault("intent", "")
        );
    }

    @Override
    public boolean isWaiting(String sessionId) {
        return getState(sessionId) == SessionState.WAITING;
    }

    // ============ 分布式锁 ============

    @Override
    public boolean acquireClaimLock(String sessionId, Long agentId) {
        String lockKey = CLAIM_LOCK_PREFIX + sessionId;
        String agentIdStr = agentId.toString();
        Boolean acquired = redis.opsForValue()
                .setIfAbsent(lockKey, agentIdStr, LOCK_TTL.toSeconds(), TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void releaseClaimLock(String sessionId) {
        redis.delete(CLAIM_LOCK_PREFIX + sessionId);
    }

    // ============ 等待队列 FIFO ============

    @Override
    public void addToWaitQueue(String sessionId) {
        redis.opsForZSet().add(WAIT_QUEUE_KEY, sessionId, System.currentTimeMillis());
    }

    @Override
    public void removeFromWaitQueue(String sessionId) {
        redis.opsForZSet().remove(WAIT_QUEUE_KEY, sessionId);
    }

    @Override
    public String peekOldestWaiting() {
        Set<String> top = redis.opsForZSet().range(WAIT_QUEUE_KEY, 0, 0);
        return (top != null && !top.isEmpty()) ? top.iterator().next() : null;
    }

    @Override
    public long getWaitQueueSize() {
        Long size = redis.opsForZSet().zCard(WAIT_QUEUE_KEY);
        return size != null ? size : 0;
    }

    @Override
    public long getWaitQueuePosition(String sessionId) {
        Long rank = redis.opsForZSet().rank(WAIT_QUEUE_KEY, sessionId);
        return rank != null ? rank + 1 : 0;
    }

    @Override
    public long getEstimatedWaitTime(String sessionId) {
        long position = getWaitQueuePosition(sessionId);
        if (position <= 0) return 0;
        long avgHandle = getAverageHandleTime();
        return position * avgHandle;
    }

    @Override
    public List<Map<String, Object>> getAllWaitingSessionDetails() {
        Set<String> sessionIds = redis.opsForZSet().range(WAIT_QUEUE_KEY, 0, -1);
        if (sessionIds == null || sessionIds.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        long position = 1;
        for (String sid : sessionIds) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("sessionId", sid);
            detail.put("position", position);
            detail.put("estimatedWait", position * getAverageHandleTime());
            SessionInfo info = getSessionInfo(sid);
            detail.put("userId", info.userId());
            detail.put("intent", info.intent());
            result.add(detail);
            position++;
        }
        return result;
    }

    @Override
    public void recordAverageHandleTime(long avgSeconds) {
        redis.opsForValue().set(AVG_HANDLE_TIME_KEY, String.valueOf(avgSeconds));
    }

    @Override
    public long getAverageHandleTime() {
        String v = redis.opsForValue().get(AVG_HANDLE_TIME_KEY);
        if (v == null) return DEFAULT_AVG_HANDLE_SECONDS;
        try { return Long.parseLong(v); }
        catch (NumberFormatException e) { return DEFAULT_AVG_HANDLE_SECONDS; }
    }

    // ============ 心跳与在线状态 ============

    @Override
    public void markAgentOnline(Long agentId) {
        redis.opsForSet().add(AGENT_ONLINE_SET, agentId.toString());
        refreshAgentHeartbeat(agentId);
        log.info("Agent {} → ONLINE", agentId);
    }

    @Override
    public void markAgentOffline(Long agentId) {
        redis.opsForSet().remove(AGENT_ONLINE_SET, agentId.toString());
        redis.delete(AGENT_HEARTBEAT_PREFIX + agentId);
        log.info("Agent {} → OFFLINE", agentId);
    }

    @Override
    public void refreshAgentHeartbeat(Long agentId) {
        redis.opsForValue().set(
                AGENT_HEARTBEAT_PREFIX + agentId,
                String.valueOf(System.currentTimeMillis()),
                120, TimeUnit.SECONDS);
    }

    @Override
    public boolean isAgentOnline(Long agentId) {
        return Boolean.TRUE.equals(redis.hasKey(AGENT_HEARTBEAT_PREFIX + agentId));
    }

    @Override
    public Set<Long> getOnlineAgents() {
        Set<String> members = redis.opsForSet().members(AGENT_ONLINE_SET);
        if (members == null || members.isEmpty()) return Collections.emptySet();
        return members.stream()
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    @Override
    public List<String> getAgentActiveSessions(Long agentId) {
        String key = AGENT_SESSIONS_PREFIX + agentId;
        Set<String> sessions = redis.opsForSet().members(key);
        return sessions != null ? new ArrayList<>(sessions) : Collections.emptyList();
    }

    @Override
    public void addAgentActiveSession(Long agentId, String sessionId) {
        redis.opsForSet().add(AGENT_SESSIONS_PREFIX + agentId, sessionId);
        redis.expire(AGENT_SESSIONS_PREFIX + agentId, TTL);
    }

    @Override
    public void removeAgentActiveSession(Long agentId, String sessionId) {
        redis.opsForSet().remove(AGENT_SESSIONS_PREFIX + agentId, sessionId);
    }

    @Override
    public Set<Long> expireStaleHeartbeats(long timeoutSeconds) {
        Set<String> onlineMembers = redis.opsForSet().members(AGENT_ONLINE_SET);
        if (onlineMembers == null || onlineMembers.isEmpty()) return Collections.emptySet();
        Set<Long> expired = new HashSet<>();
        long now = System.currentTimeMillis();
        long threshold = timeoutSeconds * 1000L;

        for (String member : onlineMembers) {
            String hbVal = redis.opsForValue().get(AGENT_HEARTBEAT_PREFIX + member);
            if (hbVal == null) {
                expired.add(Long.parseLong(member));
                continue;
            }
            try {
                long lastHb = Long.parseLong(hbVal);
                if (now - lastHb > threshold) {
                    expired.add(Long.parseLong(member));
                }
            } catch (NumberFormatException e) {
                expired.add(Long.parseLong(member));
            }
        }

        for (Long agentId : expired) {
            markAgentOffline(agentId);
            log.warn("Agent {} heartbeat expired, marked offline", agentId);
        }

        return expired;
    }

    // ============ AI 阻断开关 ============

    @Override
    public void setAiBlocked(String sessionId, boolean blocked) {
        redis.opsForHash().put(KEY_PREFIX + sessionId, "ai_blocked", blocked ? "1" : "0");
        log.debug("Session {} ai_blocked={}", sessionId, blocked);
    }

    @Override
    public boolean isAiBlocked(String sessionId) {
        String v = (String) redis.opsForHash().get(KEY_PREFIX + sessionId, "ai_blocked");
        return "1".equals(v);
    }

    // ============ 辅助方法 ============

    @Override
    public void setUserInfo(String sessionId, Long userId, String intent) {
        String key = KEY_PREFIX + sessionId;
        redis.opsForHash().putAll(key, Map.of(
                "userId", userId != null ? userId.toString() : "",
                "intent", intent != null ? intent : ""
        ));
        redis.expire(key, TTL);
    }

    private SessionState parseState(String v) {
        if (v == null) return SessionState.AI;
        try { return SessionState.valueOf(v); }
        catch (IllegalArgumentException e) { return SessionState.AI; }
    }

    private Long parseLong(String v) {
        if (v == null || v.isEmpty()) return null;
        try { return Long.parseLong(v); }
        catch (NumberFormatException e) { return null; }
    }
}
