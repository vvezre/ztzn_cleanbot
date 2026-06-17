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
 * 统一命令状态查询接口。
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

    @GetMapping("/{commandId}")
    public CommandStatusSnapshot getCommandStatus(@PathVariable String commandId) {
        log.info("查询命令状态 - commandId: {}", commandId);
        return commandStatusService.getCommandStatus(commandId);
    }

    @GetMapping("/device/{deviceId}/latest")
    public CommandStatusSnapshot getLatestCommandStatusByDevice(@PathVariable String deviceId) {
        log.info("查询设备最近命令状态 - deviceId: {}", deviceId);
        return commandStatusService.getLatestCommandStatusByDevice(deviceId);
    }
}
