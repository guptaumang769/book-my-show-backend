package com.umang.bookmyshow.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Times and logs every public {@code *Service} method via an {@code @Around} advice, warning on
 * slow calls. Keeps the timing concern in one place instead of scattered across the services.
 */
@Aspect
@Component
public class ServiceLoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceLoggingAspect.class);
    private static final long SLOW_CALL_MS = 500;

    /** Any method on a Spring bean whose class name ends in "Service" in our service package. */
    @Pointcut("execution(public * com.umang.bookmyshow.service..*Service.*(..))")
    public void serviceMethods() {
    }

    @Around("serviceMethods()")
    public Object logAround(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().getDeclaringType().getSimpleName()
                + "." + pjp.getSignature().getName();
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            long ms = (System.nanoTime() - start) / 1_000_000;
            if (ms >= SLOW_CALL_MS) {
                log.warn("{} completed in {}ms (slow)", method, ms);
            } else if (log.isDebugEnabled()) {
                log.debug("{} completed in {}ms", method, ms);
            }
            return result;
        } catch (Throwable t) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.error("{} failed after {}ms: {}", method, ms, t.toString());
            throw t;
        }
    }
}
