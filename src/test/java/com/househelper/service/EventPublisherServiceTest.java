package com.househelper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.househelper.model.SystemEvent;
import com.househelper.repository.SystemEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventPublisherServiceTest {

    @Mock
    private SystemEventRepository systemEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventPublisherService eventPublisherService;

    @Test
    @DisplayName("Serializes and stores an event with all related entity identifiers")
    void publishEvent() throws JsonProcessingException {
        Object payload = Map.of("status", "CONFIRMED");
        when(objectMapper.writeValueAsString(payload)).thenReturn("{\"status\":\"CONFIRMED\"}");
        when(systemEventRepository.save(any(SystemEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemEvent event = eventPublisherService.publishEvent(
                "BOOKING_CONFIRMED", "Booking", "41", 7L, 8L, 9L, 41L, 10L, payload);

        assertEquals("BOOKING_CONFIRMED", event.getEventType());
        assertEquals("Booking", event.getAggregateType());
        assertEquals("41", event.getAggregateId());
        assertEquals(7L, event.getHelperId());
        assertEquals(8L, event.getCustomerId());
        assertEquals(9L, event.getPaymentId());
        assertEquals(41L, event.getBookingId());
        assertEquals(10L, event.getSeriesId());
        assertEquals("{\"status\":\"CONFIRMED\"}", event.getPayload());
        verify(systemEventRepository).save(event);
    }

    @Test
    @DisplayName("Preserves nullable related identifiers when storing an event")
    void publishEventWithNullIdentifiers() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(systemEventRepository.save(any(SystemEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemEvent event = eventPublisherService.publishEvent(
                "HELPER_UPDATED", "Helper", "7", 7L, null, null, null, null, Map.of());

        assertEquals(7L, event.getHelperId());
        assertEquals(null, event.getCustomerId());
        assertEquals(null, event.getPaymentId());
        assertEquals(null, event.getBookingId());
        assertEquals(null, event.getSeriesId());
    }

    @Test
    @DisplayName("Surfaces JSON serialization failures without silently dropping an audit event")
    void publishEventSerializationFailure() throws JsonProcessingException {
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("serialization failed") { });

        assertThrows(IllegalStateException.class, () -> eventPublisherService.publishEvent(
                "BOOKING_CREATED", "Booking", "41", null, null, null, 41L, null, new Object()));

        verifyNoInteractions(systemEventRepository);
    }
}
