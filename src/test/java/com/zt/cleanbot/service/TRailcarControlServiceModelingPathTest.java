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
    void acceptsCurrentModelingPathWithoutModelIdAndRejectsInvalidProvidedId() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("modelId", "../bad");

        assertNotNull(service.validateCommand("get_modeling_path", params));
        assertNull(service.validateCommand("get_modeling_path", null));
    }

    @Test
    void acceptsModelingPointsCommandAndRejectsInvalidProvidedId() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> valid = new LinkedHashMap<>();
        valid.put("modelId", "model-1");
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("modelId", "../bad");

        assertNull(service.validateCommand("get_modeling_points", valid));
        assertNull(service.validateCommand("get_modeling_points", null));
        assertNotNull(service.validateCommand("get_modeling_points", invalid));
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
    void acceptsCurrentSessionPointAndRejectsPartialOrInvalidIdentifiers() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> missingGroup = new LinkedHashMap<>();
        missingGroup.put("modelId", "model-1");
        Map<String, Object> invalidModel = new LinkedHashMap<>();
        invalidModel.put("modelId", "../bad");
        invalidModel.put("groupId", "group-1");

        assertNotNull(service.validateCommand("sample_modeling_point", missingGroup));
        assertNotNull(service.validateCommand("sample_modeling_point", invalidModel));
        assertNull(service.validateCommand("sample_modeling_point", null));
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
    void acceptsCurrentSessionLinkPointAndRejectsPartialOrInvalidIdentifiers() {
        TRailcarControlService service = new TRailcarControlService();
        Map<String, Object> missingLink = new LinkedHashMap<>();
        missingLink.put("modelId", "model-1");
        Map<String, Object> invalidLink = new LinkedHashMap<>();
        invalidLink.put("modelId", "model-1");
        invalidLink.put("linkId", "../bad");

        assertNotNull(service.validateCommand("sample_modeling_link_point", missingLink));
        assertNotNull(service.validateCommand("sample_modeling_link_point", invalidLink));
        assertNull(service.validateCommand("sample_modeling_link_point", null));
    }

    @Test
    void acceptsModelingSessionLifecycleCommands() {
        TRailcarControlService service = new TRailcarControlService();

        assertNull(service.validateCommand("start_modeling", null));
        assertNull(service.validateCommand("finish_modeling", null));
        assertNull(service.validateCommand("get_modeling_state", null));
        assertNull(service.validateCommand("undo_modeling_point", null));
        assertNull(service.validateCommand("clear_modeling_points", null));

        Map<String, Object> area = new LinkedHashMap<>();
        area.put("pointType", "area");
        assertNull(service.validateCommand("undo_modeling_point", area));

        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("pointType", "boundary");
        assertNotNull(service.validateCommand("clear_modeling_points", invalid));
    }
}
