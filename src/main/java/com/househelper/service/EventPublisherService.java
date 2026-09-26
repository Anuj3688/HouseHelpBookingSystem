package com.househelper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.househelper.model.SystemEvent;
import com.househelper.repository.SystemEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPublisherService {

    private final SystemEventRepository systemEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SystemEvent publishEvent(String eventType, String aggregateType, String aggregateId, Object payload) {
        try {
            SystemEvent event = SystemEvent.builder()
                    .eventType(eventType)
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            return systemEventRepository.save(event);
        } catch (JsonProcessingException exception) {
            log.error("Could not serialize audit event type={} for aggregateType={} aggregateId={}",
                    eventType, aggregateType, aggregateId, exception);
            throw new IllegalStateException("Could not serialize event payload for " + eventType + ".", exception);
        }
    }
}
