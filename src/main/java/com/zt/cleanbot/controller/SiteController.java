package com.zt.cleanbot.controller;
import com.zt.cleanbot.model.Site;
import com.zt.cleanbot.service.SiteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/site")
@Slf4j
public class SiteController {

    @Autowired
    private SiteService siteService;

    @GetMapping("/list")
    public List<Site> getAllSites() {
        log.info("查询所有电站");
        return siteService.getAllSites();
    }

    @PostMapping("/add")
    public boolean addSite(@RequestBody Site site) {
        log.info("新增电站");
        return siteService.save(site);
    }

    @PutMapping("/update")
    public boolean updateSite(@RequestBody Site site) {
        log.info("更新电站");
        return siteService.updateById(site);
    }

    @DeleteMapping("/{id}")
    public boolean deleteSite(@PathVariable Integer id) {
        log.info("删除电站: {}", id);
        return siteService.removeById(id);
    }

    @GetMapping("/{id}")
    public Site getSiteById(@PathVariable Integer id) {
        log.info("根据ID查询电站: {}", id);
        return siteService.getById(id);
    }
}