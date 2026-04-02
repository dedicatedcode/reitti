package com.dedicatedcode.reitti.service.queue;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RedisQueueListener {
    String value();
    int concurrency() default 1;
    int numRetries() default 3;
    String deadLetterQueue() default "";
}