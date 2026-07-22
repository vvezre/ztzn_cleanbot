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

    @Test
    void acceptsModelingPointCommandWithValidIdentifiers() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("modelId", "model-1");
        params.put("groupId", "group-1");

        assertNull(service.validateCommand("sample_modeling_point", params));
    }

    @Test
    void rejectsModelingPointCommandWithoutValidIdentifiers() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> missingGroup = new LinkedHashMap<>();
        missingGroup.put("modelId", "model-1");
        Map<String, Object> invalidModel = new LinkedHashMap<>();
        invalidModel.put("modelId", "../bad");
        invalidModel.put("groupId", "group-1");

        assertNotNull(service.validateCommand("sample_modeling_point", missingGroup));
        assertNotNull(service.validateCommand("sample_modeling_point", invalidModel));
        assertNotNull(service.validateCommand("sample_modeling_point", null));
    }

    @Test
    void acceptsModelingLinkPointCommandWithValidIdentifiers() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("modelId", "model-1");
        params.put("linkId", "link-1");

        assertNull(service.validateCommand("sample_modeling_link_point", params));
    }

    @Test
    void rejectsModelingLinkPointCommandWithoutValidIdentifiers() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> missingLink = new LinkedHashMap<>();
        missingLink.put("modelId", "model-1");
        Map<String, Object> invalidLink = new LinkedHashMap<>();
        invalidLink.put("modelId", "model-1");
        invalidLink.put("linkId", "../bad");

        assertNotNull(service.validateCommand("sample_modeling_link_point", missingLink));
        assertNotNull(service.validateCommand("sample_modeling_link_point", invalidLink));
        assertNotNull(service.validateCommand("sample_modeling_link_point", null));
    }
}
