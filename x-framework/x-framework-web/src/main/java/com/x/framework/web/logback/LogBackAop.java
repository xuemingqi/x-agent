package com.x.framework.web.logback;

import com.x.framework.common.annotation.LogIgnore;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/** Log execution metadata without persisting passwords, model keys or conversation content. */
@Slf4j
@Aspect
@Component
public class LogBackAop {
    @Around("execution(* com.x.*.controller.*.*(..)) || execution(* com.x.*.service.impl.*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = AopUtils.getMostSpecificMethod(signature.getMethod(), pjp.getTarget().getClass());
        if (method.isAnnotationPresent(LogIgnore.class)) {
            return pjp.proceed();
        }
        String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        long started = System.nanoTime();
        try {
            Object result = pjp.proceed();
            log.info("method:[{}],status:[completed],durationMs:[{}]", methodName,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            return result;
        } catch (Throwable error) {
            log.warn("method:[{}],status:[failed],errorType:[{}],durationMs:[{}]", methodName,
                    error.getClass().getSimpleName(), TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            throw error;
        }
    }
}
