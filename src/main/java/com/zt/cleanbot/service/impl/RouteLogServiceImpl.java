package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.RouteLogMapper;
import com.zt.cleanbot.model.RouteLog;
import com.zt.cleanbot.service.RouteLogService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class RouteLogServiceImpl extends ServiceImpl<RouteLogMapper, RouteLog> implements RouteLogService {

    @Override
    public List<RouteLog> getAllRouteLogs() {
        return this.list();
    }
}