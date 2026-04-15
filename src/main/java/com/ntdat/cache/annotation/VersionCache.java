package com.ntdat.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface VersionCache {
    String entity();
    String userId();
    String[] extraKeys() default {};
    long ttl() default 1;
    TimeUnit unit() default TimeUnit.MINUTES;
}