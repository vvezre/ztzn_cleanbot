package com.zt.cleanbot.service.strategy;

import com.zt.cleanbot.dto.DeviceConfigRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardRailcarStrategyTest {

    @Test
    void shouldDefaultStandardDControlEnableToZeroAndKeepCorrectionLengthAtH27() {
        DeviceConfigRequest request = new DeviceConfigRequest();
        request.setDeviceId("-D01250001");
        request.setModel("-D01");
        request.setCompanyCode("ZTZN-PVC");

        String hex = new StandardRailcarStrategy().encode(request);

        assertEquals(140, hex.length());
        assertEquals("0000", word(hex, 23));
        assertEquals("0000", word(hex, 24));
        assertEquals("1000", word(hex, 27));
    }

    private String word(String hex, int hIndex) {
        int start = hIndex * 4;
        return hex.substring(start, start + 4);
    }
}
