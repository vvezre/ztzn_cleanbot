package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.SystemVersionResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统版本信息接口。
 * 提供当前云平台服务的发布批次标识，便于联调时快速确认版本。
 */
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/api/system")
@Slf4j
public class SystemVersionController {

    private static final String SERVICE_NAME = "cleanbot-cloud-platform";
    private static final String VERSION = "0.0.1-SNAPSHOT";
    private static final String BRANCH = "ros2";
    private static final String RELEASE_TAG = "ros2-20260331-version-visibility";
    private static final String UPDATED_AT = "2026-03-31";

    @GetMapping("/version")
    public SystemVersionResponse getVersion() {
        log.info("查询系统版本信息");

        SystemVersionResponse response = new SystemVersionResponse();
        response.setServiceName(SERVICE_NAME);
        response.setVersion(VERSION);
        response.setBranch(BRANCH);
        response.setReleaseTag(RELEASE_TAG);
        response.setUpdatedAt(UPDATED_AT);
        return response;
    }
}
