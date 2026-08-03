package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.CommandStatusSnapshot;
import com.zt.cleanbot.service.CommandStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一的异步命令状态查询接口。
 *
 * <p>{@code POST /api/t-railcar/command} 把命令发布到 MQTT 后，会立即返回 commandId。
 * 由于小车执行需要时间，该响应中的 DISPATCHED 仅表示“云平台已经发出”；前端使用本接口
 * 查询同一个 commandId，才能知道小车是否接收、正在执行、执行成功或执行失败。</p>
 *
 * <p>状态含义：</p>
 * <ul>
 *   <li>PENDING：云平台已登记，尚未完成 MQTT 发布；</li>
 *   <li>DISPATCHED：MQTT 已发布，等待小车回包；</li>
 *   <li>ACCEPTED/RUNNING：小车已接收或正在执行；</li>
 *   <li>SUCCEEDED：小车明确返回成功；业务数据位于 detail.result.data；</li>
 *   <li>FAILED/TIMEOUT：小车返回失败，或规定时间内没有最终回包。</li>
 * </ul>
 */
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/api/command-status")
@Slf4j
public class CommandStatusController {

    private final CommandStatusService commandStatusService;

    public CommandStatusController(CommandStatusService commandStatusService) {
        this.commandStatusService = commandStatusService;
    }

    /**
     * 按命令唯一编号查询一次调用的完整状态。
     * 点位记录、结束建模、保存路线等需要结果数据的命令，应以此接口的终态为准。
     */
    @GetMapping("/{commandId}")
    public CommandStatusSnapshot getCommandStatus(@PathVariable String commandId) {
        log.info("查询命令状态 - commandId: {}", commandId);
        return commandStatusService.getCommandStatus(commandId);
    }

    /**
     * 查询某台设备最近处理的一条命令，主要用于设备监控和故障排查。
     * 业务页面同时发出多条命令时，应优先按 commandId 查询，避免把其他命令误当成本次结果。
     */
    @GetMapping("/device/{deviceId}/latest")
    public CommandStatusSnapshot getLatestCommandStatusByDevice(@PathVariable String deviceId) {
        log.info("查询设备最近命令状态 - deviceId: {}", deviceId);
        return commandStatusService.getLatestCommandStatusByDevice(deviceId);
    }
}
