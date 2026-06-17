package com.zt.cleanbot.service.impl;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zt.cleanbot.dao.SiteMapper;
import com.zt.cleanbot.model.Site;
import com.zt.cleanbot.service.SiteService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class SiteServiceImpl extends ServiceImpl<SiteMapper, Site> implements SiteService {

    @Override
    public List<Site> getAllSites() {
        return this.list();
    }
}