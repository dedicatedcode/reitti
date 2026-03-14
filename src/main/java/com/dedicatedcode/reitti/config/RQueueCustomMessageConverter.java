package com.dedicatedcode.reitti.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.sonus21.rqueue.converter.GenericMessageConverter;
import com.github.sonus21.rqueue.converter.MessageConverterProvider;
import org.springframework.messaging.converter.MessageConverter;

@SuppressWarnings("unused") //is set from the application.properties
public class RQueueCustomMessageConverter implements MessageConverterProvider {
    @Override
    public MessageConverter getConverter() {
        return new GenericMessageConverter(new ObjectMapper()
                                                   .findAndRegisterModules()
                                                   .registerModule(new JavaTimeModule()));
    }
}
