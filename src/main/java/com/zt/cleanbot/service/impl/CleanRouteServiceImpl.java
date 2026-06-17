package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.CleanRouteMapper;
import com.zt.cleanbot.model.CleanRoute;
import com.zt.cleanbot.service.CleanRouteService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CleanRouteServiceImpl extends ServiceImpl<CleanRouteMapper, CleanRoute> implements CleanRouteService {

    @Override
    public List<CleanRoute> getAllCleanRoutes() {
        return this.list();
    }
}