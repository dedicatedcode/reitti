package com.dedicatedcode.reitti.config;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

/**
 * Condition that checks if a property has a non-empty value.
 */
public class ConditionalOnPropertyNotEmptyCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                metadata.getAnnotationAttributes(ConditionalOnPropertyNotEmpty.class.getName()));
        
        if (attributes == null) {
            return ConditionOutcome.noMatch("@ConditionalOnPropertyNotEmpty annotation not found");
        }

        String propertyName = getPropertyName(attributes);
        String propertyValue = context.getEnvironment().getProperty(propertyName);
        
        ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnPropertyNotEmpty.class);
        
        if (!StringUtils.hasText(propertyValue)) {
            return ConditionOutcome.noMatch(message.because("property '" + propertyName + "' is empty or not set"));
        }
        
        return ConditionOutcome.match(message.because("property '" + propertyName + "' has value: " + propertyValue));
    }

    private String getPropertyName(AnnotationAttributes attributes) {
        String prefix = attributes.getString("prefix");
        String name = attributes.getString("name");
        String value = attributes.getString("value");
        
        // Use 'value' if 'name' is not specified
        String propertyName = StringUtils.hasText(name) ? name : value;
        
        if (StringUtils.hasText(prefix)) {
            propertyName = prefix + "." + propertyName;
        }
        
        return propertyName;
    }
}
