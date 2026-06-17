package com.zt.cleanbot.model;

import lombok.Data;

@Data
public class Role {
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Integer roleId;
    private String name;
    private String permissions;
}