package com.zt.cleanbot.dto.ai;

import lombok.Data;

/**
 * 后端返回给小程序的 AI 对话响应
 */
@Data
public class AiChatResponse {

    /** 会话 ID（前端下次对话时需要带上） */
    private String sessionId;

    /** AI 回复内容 */
    private String reply;

    /** 是否成功 */
    private boolean success;

    /** 错误信息（success=false 时） */
    private String errorMessage;

    public static AiChatResponse ok(String sessionId, String reply) {
        AiChatResponse r = new AiChatResponse();
        r.setSessionId(sessionId);
        r.setReply(reply);
        r.setSuccess(true);
        return r;
    }

    public static AiChatResponse error(String sessionId, String errorMessage) {
        AiChatResponse r = new AiChatResponse();
        r.setSessionId(sessionId);
        r.setSuccess(false);
        r.setErrorMessage(errorMessage);
        r.setReply("抱歉，我遇到了一些问题，请稍后再试。");
        return r;
    }
}
