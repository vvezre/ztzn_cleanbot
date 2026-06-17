package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.CleanArea;
import java.util.List;

public interface CleanAreaService extends IService<CleanArea> {
    List<CleanArea> getAllCleanAreas();
}