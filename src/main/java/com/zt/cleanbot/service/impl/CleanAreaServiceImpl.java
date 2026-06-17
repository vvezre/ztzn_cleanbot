package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.CleanAreaMapper;
import com.zt.cleanbot.model.CleanArea;
import com.zt.cleanbot.service.CleanAreaService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CleanAreaServiceImpl extends ServiceImpl<CleanAreaMapper, CleanArea> implements CleanAreaService {

    @Override
    public List<CleanArea> getAllCleanAreas() {
        return this.list();
    }
}