package com.umang.bookmyshow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** Unit tests for the seat-lock logic (RedisTemplate mocked): all-or-nothing multi-seat acquisition. */
@ExtendWith(MockitoExtension.class)
class SeatLockServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SeatLockService seatLockService;

    @BeforeEach
    void setUp() {
        seatLockService = new SeatLockService(redisTemplate);
    }

    @Test
    void acquireLock_returnsTrue_whenKeyWasAbsent() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

        boolean acquired = seatLockService.acquireLock(1L, 10L, 100L);

        assertThat(acquired).isTrue();
    }

    @Test
    void acquireLock_returnsFalse_whenKeyAlreadyHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        boolean acquired = seatLockService.acquireLock(1L, 10L, 100L);

        assertThat(acquired).isFalse();
    }

    @Test
    void acquireLocks_allSucceed_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);

        boolean acquired = seatLockService.acquireLocks(1L, List.of(10L, 11L, 12L), 100L);

        assertThat(acquired).isTrue();
        verify(valueOps, times(3)).setIfAbsent(any(), any(), any(Duration.class));
    }

    @Test
    void acquireLocks_rollsBackAcquiredLocks_whenOneFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        // First two seats lock; the third is already taken.
        when(valueOps.setIfAbsent(any(), any(), any(Duration.class)))
                .thenReturn(true)
                .thenReturn(true)
                .thenReturn(false);

        boolean acquired = seatLockService.acquireLocks(1L, List.of(10L, 11L, 12L), 100L);

        assertThat(acquired).isFalse();
        // The two locks that were acquired before the failure must be released.
        verify(redisTemplate).delete(anyList());
    }

    @Test
    void releaseLock_deletesTheExpectedKey() {
        seatLockService.releaseLock(1L, 10L);

        verify(redisTemplate).delete(eq("seat:lock:1:10"));
    }
}
