package com.zt.cleanbot.controller;

import com.zt.cleanbot.dto.ai.AiChatRequest;
import com.zt.cleanbot.dto.ai.AiChatResponse;
import com.zt.cleanbot.service.AiChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * AI 对话接口
 * POST /api/ai/chat   — 发送消息
 * DELETE /api/ai/chat/{sessionId} — 清除会话
 */
@CrossOrigin(origins = {"*"}, maxAge = 3600L)
@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiChatController {

    @Autowired
    private AiChatService aiChatService;

    /**
     * 发送消息（需要 JWT 认证）
     *
     * 请求体：
     * {
     *   "sessionId": "xxx",   // 首次可不传，后端会生成
     *   "message": "D01现在电量多少？"
     * }
     */
    @PostMapping("/chat")
    public AiChatResponse chat(@RequestBody AiChatRequest request) {
        log.info("[AiChatController] 收到对话请求, sessionId={}, message={}",
                request.getSessionId(), request.getMessage());

        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            AiChatResponse err = new AiChatResponse();
            err.setSuccess(false);
            err.setErrorMessage("消息不能为空");
            err.setReply("请输入您的问题。");
            return err;
        }

        try {
            return aiChatService.chat(request);
        } catch (Exception e) {
            log.error("[AiChatController] 处理异常: {}", e.getMessage(), e);
            return AiChatResponse.error(request.getSessionId(), e.getMessage());
        }
    }

    /**
     * 清除会话上下文（开始新对话）
     */
    @DeleteMapping("/chat/{sessionId}")
    public AiChatResponse clearSession(@PathVariable String sessionId) {
        aiChatService.clearSession(sessionId);
        AiChatResponse resp = new AiChatResponse();
        resp.setSessionId(sessionId);
        resp.setSuccess(true);
        resp.setReply("会话已清除，可以开始新对话。");
        return resp;
    }
}
