package com.dedicatedcode.reitti.controller.api.v3;

import com.dedicatedcode.reitti.IntegrationTest;
import com.dedicatedcode.reitti.TestingService;
import com.dedicatedcode.reitti.model.geo.GeoPoint;
import com.dedicatedcode.reitti.model.geo.RawLocationPoint;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.repository.RawLocationPointJdbcService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class LocationStreamV3IntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TestingService testingService;
    @Autowired
    private RawLocationPointJdbcService rawLocationPointJdbcService;

    @Test
    void streamContainsExactEpochSeconds() throws Exception {
        User owner = testingService.randomUser();
        Instant first = Instant.parse("2020-05-09T14:00:37.069Z");
        Instant second = Instant.parse("2020-05-09T14:03:03.139Z");

        rawLocationPointJdbcService.create(owner, new RawLocationPoint(first, GeoPoint.from(53.5, 9.9), 10.0));
        rawLocationPointJdbcService.create(owner, new RawLocationPoint(second, GeoPoint.from(53.6, 9.8), 12.0));

        MvcResult asyncResult = mockMvc.perform(get("/api/v3/locations/stream/{userId}", owner.getId())
                                                       .param("start", "2020-05-09")
                                                       .param("end", "2020-05-09")
                                                       .with(user(owner)))
                .andExpect(request().asyncStarted())
                .andReturn();

        asyncResult.getAsyncResult(5000);
        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertEquals(40, body.length);

        ByteBuffer buffer = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        List<Long> timestamps = new ArrayList<>();
        while (buffer.remaining() >= 20) {
            buffer.getFloat();
            buffer.getFloat();
            buffer.getFloat();
            timestamps.add(Integer.toUnsignedLong(buffer.getInt()));
            buffer.getFloat();
        }

        assertEquals(List.of(first.getEpochSecond(), second.getEpochSecond()), timestamps);
    }
}
