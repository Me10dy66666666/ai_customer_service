package com.example.backend.infrastructure.session;

import com.example.backend.domain.chat.model.SessionState;
import com.example.backend.infrastructure.messaging.MessageRouter;
import com.example.backend.infrastructure.messaging.RedisStreamAdapter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 定时扫描 Redis 中 WAITING 状态的会话，超过阈值后自动关闭。
 * 运行频率：每 30 秒。
 * 超时阈值：WAITING 状态超过 2 分钟无人接 → 自动关闭。
 */
@Slf4j
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class SessionTimeoutJob {

    private final StringRedisTemplate redis;
    private final MessageRouter messageRouter;
    private final RedisStreamAdapter redisStreamAdapter;

    private static final long WAITING_TIMEOUT_MS = 2L * 60L * 1000L;
    private static final long HUMAN_TIMEOUT_MS = 30L * 60L * 1000L;

    public SessionTimeoutJob(StringRedisTemplate redis,
                              MessageRouter messageRouter,
                              RedisStreamAdapter redisStreamAdapter) {
        this.redis = redis;
        this.messageRouter = messageRouter;
        this.redisStreamAdapter = redisStreamAdapter;
    }

    @Scheduled(fixedRate = 30_000)
    @SchedulerLock(name = "sessionTimeoutCheck", lockAtMostFor = "25s", lockAtLeastFor = "5s")
    public void checkTimeoutSessions() {
        ScanOptions options = ScanOptions.scanOptions().match("session:*").build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                String sessionId = key.replace("session:", "");

                Map<Object, Object> fields = redis.opsForHash().entries(key);
                String status = (String) fields.get("status");
                if (status == null) continue;

                long now = System.currentTimeMillis();
                Long lastAt = parseLong((String) fields.get("lastActivityAt"));

                if (shouldTimeout(status, lastAt, now)) {
                    handleTimeout(sessionId, status);
                }
            }
        } catch (Exception e) {
            log.warn("Session timeout job scan error: {}", e.getMessage());
        }
    }

    private boolean shouldTimeout(String status, Long lastAt, long now) {
        if (status == null || lastAt == null) return false;
        if (SessionState.WAITING.name().equals(status)) {
            return (now - lastAt) > WAITING_TIMEOUT_MS;
        }
        if (SessionState.HUMAN.name().equals(status)) {
            return (now - lastAt) > HUMAN_TIMEOUT_MS;
        }
        return false;
    }

    private void handleTimeout(String sessionId, String status) {
        log.info("Session {} timed out, status={}, closing", sessionId, status);
        messageRouter.closeSession(sessionId);
        if (redisStreamAdapter != null) {
            redisStreamAdapter.publishToStream(
                    RedisStreamAdapter.USER_STREAM_PREFIX + sessionId,
                    Map.of("type", "session_timeout",
                            "content", "会话超时，系统已自动关闭"));
        }
    }

    private Long parseLong(String v) {
        if (v == null) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
