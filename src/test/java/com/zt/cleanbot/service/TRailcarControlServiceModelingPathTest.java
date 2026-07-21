package com.zt.cleanbot.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TRailcarControlServiceModelingPathTest {

    @Test
    void acceptsModelingPathCommandWithValidModelId() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("modelId", "model-1");

        assertNull(service.validateCommand("get_modeling_path", params));
    }

    @Test
    void rejectsModelingPathCommandWithoutValidModelId() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("modelId", "../bad");

        assertNotNull(service.validateCommand("get_modeling_path", params));
        assertNotNull(service.validateCommand("get_modeling_path", null));
    }
}
