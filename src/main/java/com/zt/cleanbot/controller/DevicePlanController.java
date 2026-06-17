package com.zt.cleanbot.controller;

import com.zt.cleanbot.common.Result;
import com.zt.cleanbot.model.DevicePlan;
import com.zt.cleanbot.service.DevicePlanService;
import com.zt.cleanbot.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 设备计划任务 Controller
 */
@RestController
@RequestMapping("/api/plans")
public class DevicePlanController {

    @Autowired
    private DevicePlanService devicePlanService;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 获取指定设备的计划列表
     */
    @GetMapping
    public Result<List<DevicePlan>> getPlans(@RequestParam String deviceId) {
        List<DevicePlan> plans = devicePlanService.getPlansByDeviceId(deviceId);
        return Result.success(plans);
    }

    /**
     * 添加新计划
     */
    @PostMapping
    public Result<DevicePlan> addPlan(@RequestBody DevicePlan plan, HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    Integer userId = jwtUtil.getUserIdFromToken(token);
                    if (userId != null) {
                        plan.setUserId(userId);
                    }
                }
            } catch (Exception e) {
                // Ignore token error, allow to proceed with null userId or handle globally
            }
        }
        boolean success = devicePlanService.save(plan);
        if (success) {
            return Result.success(plan);
        } else {
            return Result.error(500, "添加计划失败");
        }
    }

    /**
     * 更新计划
     */
    @PutMapping("/{id}")
    public Result<DevicePlan> updatePlan(@PathVariable Integer id, @RequestBody DevicePlan plan) {
        plan.setId(id);
        boolean success = devicePlanService.updateById(plan);
        if (success) {
            return Result.success(plan);
        } else {
            return Result.error(500, "更新计划失败");
        }
    }

    /**
     * 删除计划
     */
    @DeleteMapping("/{id}")
    public Result<String> deletePlan(@PathVariable Integer id) {
        boolean success = devicePlanService.removeById(id);
        if (success) {
            return Result.success("删除成功");
        } else {
            return Result.error(500, "删除失败");
        }
    }
}
