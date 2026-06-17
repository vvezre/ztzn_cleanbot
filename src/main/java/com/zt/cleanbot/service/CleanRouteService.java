package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.CleanRoute;
import java.util.List;

public interface CleanRouteService extends IService<CleanRoute> {
    List<CleanRoute> getAllCleanRoutes();
}