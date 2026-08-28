package com.umang.bookmyshow.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Redis-backed distributed lock, the fast first line of defense against two users
 * grabbing the same seat. It fails fast without touching the DB; the pessimistic
 * row lock in the booking transaction is the durable backstop.
 */
@Service
@RequiredArgsConstructor
public class SeatLockService {

    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "seat:lock:";

    /** Atomic check-then-delete: removes the key only if its value still matches the caller's token. */
    private static final DefaultRedisScript<Long> SAFE_RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) "
                    + "else return 0 end",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    public boolean acquireLock(Long showId, Long seatId, Long userId) {
        String lockKey = buildLockKey(showId, seatId);
        String lockValue = buildLockValue(userId);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, LOCK_DURATION);
        return Boolean.TRUE.equals(acquired);
    }

    /** Acquires all seat locks or none — rolls back partial acquisitions on the first failure. */
    public boolean acquireLocks(Long showId, List<Long> seatIds, Long userId) {
        List<String> acquiredLocks = new ArrayList<>();
        try {
            for (Long seatId : seatIds) {
                boolean acquired = acquireLock(showId, seatId, userId);
                if (!acquired) {
                    releaseLocks(acquiredLocks);
                    return false;
                }
                acquiredLocks.add(buildLockKey(showId, seatId));
            }
            return true;
        } catch (RuntimeException e) {
            releaseLocks(acquiredLocks);
            throw e;
        }
    }

    public void releaseLock(Long showId, Long seatId) {
        redisTemplate.delete(buildLockKey(showId, seatId));
    }

    public void releaseLocks(List<String> lockKeys) {
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    /**
     * Ownership-checked release of a batch: each lock is removed only if this user still holds
     * it (via {@link #safeRelease}). Used by the booking/cancel/expiry paths so we never delete
     * a lock that expired and was re-acquired by a different user in the meantime.
     */
    public void releaseLocks(Long showId, List<Long> seatIds, Long userId) {
        if (seatIds == null) {
            return;
        }
        for (Long seatId : seatIds) {
            safeRelease(showId, seatId, userId);
        }
    }

    /** Ownership-checked release: only removes the lock if this user still holds it. */
    public boolean safeRelease(Long showId, Long seatId, Long userId) {
        Long deleted = redisTemplate.execute(
                SAFE_RELEASE_SCRIPT,
                Collections.singletonList(buildLockKey(showId, seatId)),
                buildLockValue(userId));
        return deleted != null && deleted > 0;
    }

    private String buildLockKey(Long showId, Long seatId) {
        return KEY_PREFIX + showId + ":" + seatId;
    }

    private String buildLockValue(Long userId) {
        return String.valueOf(userId);
    }
}
