package com.dedicatedcode.reitti.service.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Component
public class RedisQueueAnnotationProcessor {
    private static final Logger log = LoggerFactory.getLogger(RedisQueueAnnotationProcessor.class);

    private final ApplicationContext applicationContext;

    private final RedisQueueService redisQueueService;

    public RedisQueueAnnotationProcessor(ApplicationContext applicationContext, RedisQueueService redisQueueService) {
        this.applicationContext = applicationContext;
        this.redisQueueService = redisQueueService;
    }

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationEvent(ContextRefreshedEvent event) {
        scanAndRegisterListeners();
    }

    private void scanAndRegisterListeners() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Method[] methods = bean.getClass().getMethods();

            for (Method method : methods) {
                RedisQueueListener annotation = method.getAnnotation(RedisQueueListener.class);
                if (annotation != null) {
                    registerListener(bean, method, annotation, this.redisQueueService);
                }
            }
        }
    }

    private void registerListener(Object bean, Method method, RedisQueueListener annotation, RedisQueueService queueService) {
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException(
                    "Method " + method.getName() + " must have exactly one parameter");
        }

        Class<?> payloadType = method.getParameterTypes()[0];

        MessageHandler<Object> handler = (MessageHandler<Object>) createHandler(bean, method, payloadType);

        queueService.registerHandler(
                annotation.value(),
                payloadType,
                handler,
                annotation.concurrency(),
                annotation.numRetries(),
                annotation.deadLetterQueue()
        );
    }

    private <T> MessageHandler<T> createHandler(Object bean, Method method, Class<T> payloadType) {
        return (payload) -> {
            try {
                method.invoke(bean, payload);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                if (target instanceof Exception) {
                    throw (Exception) target;
                } else if (target instanceof Error) {
                    throw (Error) target;
                } else {
                    throw new RuntimeException("Unexpected throwable", target);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to invoke method", e);
            }
        };
    }
}