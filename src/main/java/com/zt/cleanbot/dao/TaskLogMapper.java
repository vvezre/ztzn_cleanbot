package com.zt.cleanbot.dao;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zt.cleanbot.dto.CityTaskRankDTO;
import com.zt.cleanbot.dto.CityTaskStatsDTO;
import com.zt.cleanbot.dto.MonthlyFailStatsDTO;
import com.zt.cleanbot.model.TaskLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface TaskLogMapper extends BaseMapper<TaskLog> {

    // 新增：统计各市任务数据
    List<CityTaskStatsDTO> selectCityTaskStats(@Param("startDate") Date startDate,
                                               @Param("endDate") Date endDate);

    // 新增：统计最近六个月每月失败情况
    List<MonthlyFailStatsDTO> selectMonthlyFailStats();

    // 修改：当前月市级任务成功数量排名前十
    List<CityTaskRankDTO> selectCityTaskRankCurrentMonth();

}