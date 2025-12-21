package com.dedicatedcode.reitti.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Conditional annotation that checks if a property has a non-empty value.
 * This is a convenience annotation that combines ConditionalOnProperty with
 * matchIfMissing=false and havingValue="" (empty string) negated.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@org.springframework.context.annotation.Conditional(ConditionalOnPropertyNotEmptyCondition.class)
public @interface ConditionalOnPropertyNotEmpty {

    /**
     * The name of the property to test.
     * @return the property name
     */
    String name() default "";

    /**
     * The name of the property to test (alias for name).
     * @return the property name
     */
    String value() default "";

    /**
     * A prefix that should be applied to each property name.
     * @return the prefix
     */
    String prefix() default "";
}
