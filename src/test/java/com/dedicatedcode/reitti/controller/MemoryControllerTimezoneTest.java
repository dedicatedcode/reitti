package com.dedicatedcode.reitti.controller;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.memory.MemoryDTO;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.MemoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@Transactional
public class MemoryControllerTimezoneTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestingService testingService;

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCreateAndRetrieveMemoryWithDifferentTimezones() throws Exception {
        User user = testingService.randomUser();
        
        // Test data: Create memory for a specific local date range
        LocalDate startDate = LocalDate.of(2023, 6, 15);
        LocalDate endDate = LocalDate.of(2023, 6, 17);
        
        // Test different timezones
        ZoneId[] timezones = {
            ZoneId.of("UTC"),
            ZoneId.of("Europe/Berlin"),
            ZoneId.of("America/New_York"),
            ZoneId.of("Asia/Tokyo"),
            ZoneId.of("Australia/Sydney")
        };
        
        for (ZoneId timezone : timezones) {
            // Create memory with specific timezone
            MvcResult createResult = mockMvc.perform(post("/memories")
                    .with(user(user))
                    .param("title", "Test Memory " + timezone.getId())
                    .param("description", "Test description")
                    .param("startDate", startDate.toString())
                    .param("endDate", endDate.toString())
                    .param("timezone", timezone.getId()))
                    .andExpect(status().isOk())
                    .andReturn();
            
            // Extract memory ID from redirect header
            String redirectHeader = createResult.getResponse().getHeader("HX-Redirect");
            assertThat(redirectHeader).isNotNull();
            Long memoryId = Long.parseLong(redirectHeader.substring("/memories/".length()));
            
            // Retrieve memory DTO with the same timezone
            MvcResult dtoResult = mockMvc.perform(get("/memories/{id}/dto", memoryId)
                    .with(user(user))
                    .param("timezone", timezone.getId()))
                    .andExpect(status().isOk())
                    .andReturn();
            
            String jsonResponse = dtoResult.getResponse().getContentAsString();
            MemoryDTO memoryDTO = objectMapper.readValue(jsonResponse, MemoryDTO.class);
            
            // Verify that the local dates match what we sent
            assertThat(memoryDTO.getStartDate().toLocalDate()).isEqualTo(startDate);
            assertThat(memoryDTO.getEndDate().toLocalDate()).isEqualTo(endDate);
            assertThat(memoryDTO.getTimezone()).isEqualTo(timezone);
            
            // Verify that the start time is at the beginning of the day in the specified timezone
            LocalDateTime expectedStartDateTime = startDate.atStartOfDay();
            assertThat(memoryDTO.getStartDate()).isEqualTo(expectedStartDateTime);
            
            // Verify that the end time is at the end of the day in the specified timezone
            LocalDateTime expectedEndDateTime = endDate.plusDays(1).atStartOfDay().minusNanos(1);
            assertThat(memoryDTO.getEndDate()).isEqualTo(expectedEndDateTime);
            
            // Test retrieving the same memory with a different timezone
            ZoneId differentTimezone = timezone.equals(ZoneId.of("UTC")) ? 
                ZoneId.of("Europe/Berlin") : ZoneId.of("UTC");
            
            MvcResult differentTzResult = mockMvc.perform(get("/memories/{id}/dto", memoryId)
                    .with(user(user))
                    .param("timezone", differentTimezone.getId()))
                    .andExpect(status().isOk())
                    .andReturn();
            
            String differentTzJson = differentTzResult.getResponse().getContentAsString();
            MemoryDTO differentTzDTO = objectMapper.readValue(differentTzJson, MemoryDTO.class);
            
            // Verify that the UTC instants are the same regardless of timezone
            assertThat(memoryDTO.getStartDateAsInstant()).isEqualTo(differentTzDTO.getStartDateAsInstant());
            assertThat(memoryDTO.getEndDateAsInstant()).isEqualTo(differentTzDTO.getEndDateAsInstant());
            
            // But the local date times should be different (unless the dates happen to be the same)
            assertThat(differentTzDTO.getTimezone()).isEqualTo(differentTimezone);
        }
    }

    @Test
    public void testMemoryTimezoneConversionAccuracy() throws Exception {
        User user = testingService.randomUser();
        
        // Test with a specific date and timezone that has DST
        LocalDate testDate = LocalDate.of(2023, 7, 15); // Summer time
        ZoneId berlinTimezone = ZoneId.of("Europe/Berlin");
        
        // Create memory
        MvcResult createResult = mockMvc.perform(post("/memories")
                .with(user(user))
                .param("title", "DST Test Memory")
                .param("description", "Testing daylight saving time")
                .param("startDate", testDate.toString())
                .param("endDate", testDate.toString())
                .param("timezone", berlinTimezone.getId()))
                .andExpect(status().isOk())
                .andReturn();
        
        String redirectHeader = createResult.getResponse().getHeader("HX-Redirect");
        Long memoryId = Long.parseLong(redirectHeader.substring("/memories/".length()));
        
        // Retrieve with Berlin timezone
        MvcResult berlinResult = mockMvc.perform(get("/memories/{id}/dto", memoryId)
                .with(user(user))
                .param("timezone", berlinTimezone.getId()))
                .andExpect(status().isOk())
                .andReturn();
        
        MemoryDTO berlinDTO = objectMapper.readValue(berlinResult.getResponse().getContentAsString(), MemoryDTO.class);
        
        // Retrieve with UTC
        MvcResult utcResult = mockMvc.perform(get("/memories/{id}/dto", memoryId)
                .with(user(user))
                .param("timezone", "UTC"))
                .andExpect(status().isOk())
                .andReturn();
        
        MemoryDTO utcDTO = objectMapper.readValue(utcResult.getResponse().getContentAsString(), MemoryDTO.class);
        
        // Verify the time difference accounts for DST (Berlin is UTC+2 in summer)
        ZonedDateTime berlinStart = berlinDTO.getStartDate().atZone(berlinTimezone);
        ZonedDateTime utcStart = utcDTO.getStartDate().atZone(ZoneId.of("UTC"));
        
        // Convert both to the same timezone for comparison
        Instant berlinInstant = berlinStart.toInstant();
        Instant utcInstant = utcStart.toInstant();
        
        assertThat(berlinInstant).isEqualTo(utcInstant);
        
        // Verify that Berlin local time is 2 hours ahead of UTC in summer
        assertThat(berlinDTO.getStartDate().getHour()).isEqualTo(0); // Start of day in Berlin
        assertThat(utcDTO.getStartDate().getHour()).isEqualTo(22); // 22:00 previous day in UTC (Berlin is UTC+2)
    }

    @Test
    public void testMemoryCreationWithEdgeCaseTimezones() throws Exception {
        User user = testingService.randomUser();
        
        // Test with timezone that has unusual offset
        ZoneId chathamTimezone = ZoneId.of("Pacific/Chatham"); // UTC+12:45/+13:45
        LocalDate testDate = LocalDate.of(2023, 12, 31); // New Year's Eve
        
        MvcResult createResult = mockMvc.perform(post("/memories")
                .with(user(user))
                .param("title", "Edge Case Timezone Memory")
                .param("description", "Testing unusual timezone offset")
                .param("startDate", testDate.toString())
                .param("endDate", testDate.toString())
                .param("timezone", chathamTimezone.getId()))
                .andExpect(status().isOk())
                .andReturn();
        
        String redirectHeader = createResult.getResponse().getHeader("HX-Redirect");
        Long memoryId = Long.parseLong(redirectHeader.substring("/memories/".length()));
        
        // Retrieve the memory
        MvcResult result = mockMvc.perform(get("/memories/{id}/dto", memoryId)
                .with(user(user))
                .param("timezone", chathamTimezone.getId()))
                .andExpect(status().isOk())
                .andReturn();
        
        MemoryDTO memoryDTO = objectMapper.readValue(result.getResponse().getContentAsString(), MemoryDTO.class);
        
        // Verify the date is preserved correctly
        assertThat(memoryDTO.getStartDate().toLocalDate()).isEqualTo(testDate);
        assertThat(memoryDTO.getEndDate().toLocalDate()).isEqualTo(testDate);
        assertThat(memoryDTO.getTimezone()).isEqualTo(chathamTimezone);
    }
}
