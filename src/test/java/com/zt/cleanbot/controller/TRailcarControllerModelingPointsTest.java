package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.CommandStatusSnapshot;
import com.zt.cleanbot.dto.TRailcarCommandRequest;
import com.zt.cleanbot.dto.TRailcarControlResponse;
import com.zt.cleanbot.service.CommandStatusService;
import com.zt.cleanbot.service.TRailcarControlService;
import com.zt.cleanbot.service.VehicleService;
import com.zt.cleanbot.utils.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TRailcarControllerModelingPointsTest {

    private TRailcarController controller;
    private TRailcarControlService controlService;
    private CommandStatusService commandStatusService;
    private VehicleService vehicleService;
    private RedisUtil redisUtil;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new TRailcarController();
        controlService = mock(TRailcarControlService.class);
        commandStatusService = mock(CommandStatusService.class);
        vehicleService = mock(VehicleService.class);
        redisUtil = mock(RedisUtil.class);
        ReflectionTestUtils.setField(controller, "tRailcarControlService", controlService);
        ReflectionTestUtils.setField(controller, "commandStatusService", commandStatusService);
        ReflectionTestUtils.setField(controller, "vehicleService", vehicleService);
        ReflectionTestUtils.setField(controller, "redisUtil", redisUtil);

        request = new MockHttpServletRequest();
        request.setAttribute("userId", 13);
        request.setAttribute("roleId", 3);
        request.setAttribute("username", "frontend");
    }

    @Test
    void returnsOnlyTheFrontendPointList() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "get_modeling_points",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-1",
                        "trace-1"));

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", "p001");
        point.put("name", "区域点1");
        point.put("sequence", 1);
        point.put("x", 0);
        point.put("y", 0);
        point.put("lat", 32.0364);
        point.put("lon", 118.1234);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", Arrays.asList(point));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("result", result);

        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setStatus("SUCCEEDED");
        snapshot.setTerminal(true);
        snapshot.setDetail(detail);
        when(commandStatusService.waitForTerminal(eq("cmd-1"), eq(10_000L))).thenReturn(snapshot);

        ResponseEntity<Map<String, Object>> response = controller.getModelingPoints("250001", request);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        List<?> points = (List<?>) response.getBody().get("points");
        assertEquals(1, points.size());
        assertEquals("p001", ((Map<?, ?>) points.get(0)).get("id"));

        ArgumentCaptor<TRailcarCommandRequest> captor = ArgumentCaptor.forClass(TRailcarCommandRequest.class);
        verify(controlService).sendCommand(captor.capture());
        assertEquals("250001", captor.getValue().getProductId());
        assertEquals("get_modeling_points", captor.getValue().getCommand());
    }

    @Test
    void returnsOnlyTheFrontendLinkPointList() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "get_modeling_link_points",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-link-1",
                        "trace-link-1"));

        Map<String, Object> point = new LinkedHashMap<>();
        point.put("id", "lp001");
        point.put("name", "连接点1");
        point.put("sequence", 1);
        point.put("x", 0);
        point.put("y", 470);
        point.put("lat", 32.0365);
        point.put("lon", 118.1235);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("points", Arrays.asList(point));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("result", result);

        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setStatus("SUCCEEDED");
        snapshot.setTerminal(true);
        snapshot.setDetail(detail);
        when(commandStatusService.waitForTerminal(eq("cmd-link-1"), eq(10_000L))).thenReturn(snapshot);

        ResponseEntity<Map<String, Object>> response = controller.getModelingLinkPoints("250001", request);

        assertEquals(200, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        List<?> points = (List<?>) response.getBody().get("points");
        assertEquals(1, points.size());
        assertEquals("lp001", ((Map<?, ?>) points.get(0)).get("id"));

        ArgumentCaptor<TRailcarCommandRequest> captor = ArgumentCaptor.forClass(TRailcarCommandRequest.class);
        verify(controlService).sendCommand(captor.capture());
        assertEquals("250001", captor.getValue().getProductId());
        assertEquals("get_modeling_link_points", captor.getValue().getCommand());
    }

    @Test
    void returnsAreaLinkAndPathPointsInOneModelingResult() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "get_modeling_result",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-result-1",
                        "trace-result-1"));

        Map<String, Object> areaPoint = new LinkedHashMap<>();
        areaPoint.put("id", "a1");
        Map<String, Object> linkPoint = new LinkedHashMap<>();
        linkPoint.put("id", "l1");
        Map<String, Object> firstPathPoint = new LinkedHashMap<>();
        firstPathPoint.put("id", "p1");
        firstPathPoint.put("name", "\u8def\u5f84\u70b91");
        firstPathPoint.put("sequence", 1);
        firstPathPoint.put("x", 0);
        firstPathPoint.put("y", 0);
        Map<String, Object> secondPathPoint = new LinkedHashMap<>();
        secondPathPoint.put("id", "p2");
        secondPathPoint.put("name", "\u8def\u5f84\u70b92");
        secondPathPoint.put("sequence", 2);
        secondPathPoint.put("x", 100);
        secondPathPoint.put("y", 0);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("areaPoints", Arrays.asList(areaPoint));
        data.put("linkPoints", Arrays.asList(linkPoint));
        data.put("pathPoints", Arrays.asList(firstPathPoint, secondPathPoint));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("result", result);

        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setStatus("SUCCEEDED");
        snapshot.setTerminal(true);
        snapshot.setDetail(detail);
        when(commandStatusService.waitForTerminal(eq("cmd-result-1"), eq(10_000L))).thenReturn(snapshot);

        ResponseEntity<Map<String, Object>> response =
                controller.getModelingResult("250001", request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("\u89c4\u5212\u6210\u529f", response.getBody().get("message"));
        Map<?, ?> responseData = (Map<?, ?>) response.getBody().get("data");
        assertEquals(3, responseData.size());
        assertEquals(1, ((List<?>) responseData.get("areaPoints")).size());
        assertEquals(1, ((List<?>) responseData.get("linkPoints")).size());
        List<?> pathPoints = (List<?>) responseData.get("pathPoints");
        assertEquals(2, pathPoints.size());
        assertEquals("p2", ((Map<?, ?>) pathPoints.get(1)).get("id"));

        ArgumentCaptor<TRailcarCommandRequest> captor =
                ArgumentCaptor.forClass(TRailcarCommandRequest.class);
        verify(controlService).sendCommand(captor.capture());
        assertEquals("250001", captor.getValue().getProductId());
        assertEquals("get_modeling_result", captor.getValue().getCommand());
    }

    @Test
    void returnsGatewayTimeoutWhenRobotDoesNotReply() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "get_modeling_points",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-timeout",
                        "trace-timeout"));

        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setStatus("DISPATCHED");
        snapshot.setTerminal(false);
        when(commandStatusService.waitForTerminal(eq("cmd-timeout"), eq(10_000L))).thenReturn(snapshot);

        ResponseEntity<Map<String, Object>> response = controller.getModelingPoints("250001", request);

        assertEquals(504, response.getStatusCodeValue());
        assertEquals("robot response timeout", response.getBody().get("message"));
    }

    @Test
    void savesReadyModelingPlanUnderFrontendTaskName() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "save_modeling_task",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-save-1",
                        "trace-save-1"));
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("taskName", "\u5382\u533a\u8def\u7ebf\u4e00");
        resultData.put("taskCount", 30);
        resultData.put("modelId", "model-1");
        when(commandStatusService.waitForTerminal(eq("cmd-save-1"), eq(10_000L)))
                .thenReturn(succeededSnapshot(resultData));

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("productId", "250001");
        payload.put("taskName", "\u5382\u533a\u8def\u7ebf\u4e00");
        ResponseEntity<Map<String, Object>> response =
                controller.saveModelingTask(payload, request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("\u8def\u7ebf\u4fdd\u5b58\u6210\u529f", response.getBody().get("message"));
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("\u5382\u533a\u8def\u7ebf\u4e00", data.get("taskName"));
        assertEquals(30, data.get("taskCount"));

        ArgumentCaptor<TRailcarCommandRequest> captor =
                ArgumentCaptor.forClass(TRailcarCommandRequest.class);
        verify(controlService).sendCommand(captor.capture());
        assertEquals("save_modeling_task", captor.getValue().getCommand());
        assertEquals("\u5382\u533a\u8def\u7ebf\u4e00", captor.getValue().getParams().get("taskName"));
    }

    @Test
    void listsRoutesFromTheRequestedRobot() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "get_task_names",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-list-1",
                        "trace-list-1"));
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("taskNames", Arrays.asList("route-a", "route-b"));
        resultData.put("currentTaskName", "route-a");
        when(commandStatusService.waitForTerminal(eq("cmd-list-1"), eq(10_000L)))
                .thenReturn(succeededSnapshot(resultData));

        ResponseEntity<Map<String, Object>> response =
                controller.getRobotTaskOptions("250001", request);

        assertEquals(200, response.getStatusCodeValue());
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals(Arrays.asList("route-a", "route-b"), data.get("taskNames"));
        assertEquals("route-a", data.get("currentTaskName"));

        ArgumentCaptor<TRailcarCommandRequest> captor =
                ArgumentCaptor.forClass(TRailcarCommandRequest.class);
        verify(controlService).sendCommand(captor.capture());
        assertEquals("get_task_names", captor.getValue().getCommand());
        assertEquals("250001", captor.getValue().getProductId());
    }

    @Test
    void returnsAllSavedRoutePointDataFromTheRequestedRobot() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "get_saved_routes",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-routes-1",
                        "trace-routes-1"));

        Map<String, Object> route = new LinkedHashMap<>();
        route.put("taskName", "route-a");
        route.put("modelId", "model-a");
        route.put("current", true);
        route.put("areaPoints", Arrays.asList(new LinkedHashMap<String, Object>()));
        route.put("linkPoints", Arrays.asList(new LinkedHashMap<String, Object>()));
        route.put("pathPoints", Arrays.asList(
                new LinkedHashMap<String, Object>(),
                new LinkedHashMap<String, Object>()));

        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("routes", Arrays.asList(route));
        resultData.put("currentTaskName", "route-a");
        when(commandStatusService.waitForTerminal(eq("cmd-routes-1"), eq(10_000L)))
                .thenReturn(succeededSnapshot(resultData));

        ResponseEntity<Map<String, Object>> response =
                controller.getSavedRoutes("250001", request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("\u83b7\u53d6\u5df2\u4fdd\u5b58\u8def\u7ebf\u6210\u529f", response.getBody().get("message"));
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertEquals("250001", data.get("productId"));
        assertEquals("-T01250001", data.get("serialNumber"));
        assertEquals("route-a", data.get("currentTaskName"));
        List<?> routes = (List<?>) data.get("routes");
        assertEquals(1, routes.size());
        assertEquals("route-a", ((Map<?, ?>) routes.get(0)).get("taskName"));
        assertEquals(
                2,
                ((List<?>) ((Map<?, ?>) routes.get(0)).get("pathPoints")).size());

        ArgumentCaptor<TRailcarCommandRequest> captor =
                ArgumentCaptor.forClass(TRailcarCommandRequest.class);
        verify(controlService).sendCommand(captor.capture());
        assertEquals("get_saved_routes", captor.getValue().getCommand());
        assertEquals("250001", captor.getValue().getProductId());
    }

    @Test
    void returnsGatewayTimeoutWhenSavedRoutesRobotIsOffline() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "get_saved_routes",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-routes-timeout",
                        "trace-routes-timeout"));
        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setStatus("DISPATCHED");
        snapshot.setTerminal(false);
        when(commandStatusService.waitForTerminal(eq("cmd-routes-timeout"), eq(10_000L)))
                .thenReturn(snapshot);

        ResponseEntity<Map<String, Object>> response =
                controller.getSavedRoutes("250001", request);

        assertEquals(504, response.getStatusCodeValue());
        assertEquals("\u8bbe\u5907\u79bb\u7ebf\u6216\u54cd\u5e94\u8d85\u65f6", response.getBody().get("message"));
    }

    @Test
    void selectsRouteOnlyAfterRobotConfirmsTheSwitch() {
        when(vehicleService.hasDeviceAccess(13, 3, "-T01250001")).thenReturn(true);
        when(controlService.sendCommand(any(TRailcarCommandRequest.class))).thenReturn(
                TRailcarControlResponse.success(
                        "-T01250001",
                        "set_current_task",
                        "RAILCAR/S/-T01250001",
                        null,
                        "cmd-select-1",
                        "trace-select-1"));
        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("taskName", "route-b");
        when(commandStatusService.waitForTerminal(eq("cmd-select-1"), eq(10_000L)))
                .thenReturn(succeededSnapshot(resultData));

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("productId", "250001");
        payload.put("taskName", "route-b");
        ResponseEntity<Map<String, Object>> response =
                controller.selectRobotTask(payload, request);

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("\u8def\u7ebf\u9009\u62e9\u6210\u529f", response.getBody().get("message"));
        assertEquals(
                "route-b",
                ((Map<?, ?>) response.getBody().get("data")).get("taskName"));
    }

    private CommandStatusSnapshot succeededSnapshot(Map<String, Object> resultData) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", resultData);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("result", result);
        CommandStatusSnapshot snapshot = new CommandStatusSnapshot();
        snapshot.setStatus("SUCCEEDED");
        snapshot.setTerminal(true);
        snapshot.setDetail(detail);
        return snapshot;
    }
}
