package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.RouteLog;
import java.util.List;

public interface RouteLogService extends IService<RouteLog> {
    List<RouteLog> getAllRouteLogs();
}