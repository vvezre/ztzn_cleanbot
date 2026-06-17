package com.zt.cleanbot.model;

import lombok.Data;

@Data
public class User {
    @com.baomidou.mybatisplus.annotation.TableId(type = com.baomidou.mybatisplus.annotation.IdType.AUTO)
    private Integer userId;
    private String username;
    private String password;
    private Integer roleId;
    private String realName;
    private String status;
}