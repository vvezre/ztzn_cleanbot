package com.zt.cleanbot.service;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zt.cleanbot.model.Site;
import java.util.List;

public interface SiteService extends IService<Site> {
    List<Site> getAllSites();
}