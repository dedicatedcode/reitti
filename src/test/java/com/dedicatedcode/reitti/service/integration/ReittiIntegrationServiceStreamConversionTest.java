package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.ReittiRemoteInfo;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReittiIntegrationServiceStreamConversionTest {

    @Test
    void detectsV3SupportFromRemoteInfo() {
        ReittiRemoteInfo.RemoteServerInfo newServer = new ReittiRemoteInfo.RemoteServerInfo("Reitti", "1.0", LocalDateTime.now(), List.of(2, 3));
        ReittiRemoteInfo.RemoteServerInfo oldServer = new ReittiRemoteInfo.RemoteServerInfo("Reitti", "1.0", LocalDateTime.now(), null);

        assertTrue(newServer.supportsApiVersion(3));
        assertFalse(oldServer.supportsApiVersion(3));
    }

    @Test
    void convertsFloatTimestampsToExactEpochSeconds() {
        byte[] records = ByteBuffer.allocate(40)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(53.5f).putFloat(9.9f).putFloat(10.0f).putFloat(1589032832f).putFloat(3600f)
                .putFloat(53.6f).putFloat(9.8f).putFloat(12.0f).putFloat(1589032960f).putFloat(7200f)
                .array();

        ReittiIntegrationService.convertV2TimestampsToV3(records, records.length);

        ByteBuffer buffer = ByteBuffer.wrap(records).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(1589032832L, Integer.toUnsignedLong(buffer.getInt(12)));
        assertEquals(1589032960L, Integer.toUnsignedLong(buffer.getInt(32)));
        assertEquals(53.5f, buffer.getFloat(0));
        assertEquals(3600f, buffer.getFloat(16));
        assertEquals(53.6f, buffer.getFloat(20));
        assertEquals(7200f, buffer.getFloat(36));
    }

    @Test
    void leavesIncompleteRecordsUntouched() {
        byte[] records = ByteBuffer.allocate(24)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(53.5f).putFloat(9.9f).putFloat(10.0f).putFloat(1589032837.069f).putFloat(3600f)
                .put((byte) 0x01).put((byte) 0x02).put((byte) 0x03).put((byte) 0x04)
                .array();

        ReittiIntegrationService.convertV2TimestampsToV3(records, 20);

        assertEquals(1589032832L, Integer.toUnsignedLong(ByteBuffer.wrap(records).order(ByteOrder.LITTLE_ENDIAN).getInt(12)));
        assertEquals(0x01, records[20]);
        assertEquals(0x02, records[21]);
        assertEquals(0x03, records[22]);
        assertEquals(0x04, records[23]);
    }
}
