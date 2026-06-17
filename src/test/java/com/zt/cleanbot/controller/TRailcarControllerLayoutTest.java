package com.zt.cleanbot.controller;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TRailcarControllerLayoutTest {

    @Test
    void countsLayoutVersionTwoAreas() {
        Map<String, Object> v2Area = new LinkedHashMap<>();
        v2Area.put("layoutVersion", 2);

        Map<String, Object> legacyArea = new LinkedHashMap<>();
        legacyArea.put("lineCount", 4);

        assertEquals(1, TRailcarController.countLayoutV2Areas(Arrays.asList(v2Area, legacyArea)));
    }

    @Test
    void ignoresNonMapAreasWhenCountingLayoutVersionTwoAreas() {
        assertEquals(0, TRailcarController.countLayoutV2Areas(Collections.singletonList("bad-area")));
    }
}
