package com.zt.cleanbot.dto.ai;

import lombok.Data;

/**
 * 小程序发送的 AI 对话请求
 */
@Data
public class AiChatRequest {

    /**
     * 会话 ID（前端生成并持久保存，用于多轮对话上下文）
     * 为空时后端会生成新 sessionId
     */
    private String sessionId;

    /**
     * 用户输入的消息
     */
    private String message;
}
