package com.dedicatedcode.reitti.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

public class OnPropertyNotEmptyCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String propertyName = (String) metadata.getAnnotationAttributes(
                ConditionalOnPropertyNotEmpty.class.getName()).get("value");
        
        String propertyValue = context.getEnvironment().getProperty(propertyName);
        return StringUtils.hasText(propertyValue);
    }
}
