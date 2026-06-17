package com.zt.cleanbot.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对话消息（OpenAI 格式兼容）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 角色：system / user / assistant / tool */
    private String role;

    /** 文本内容（tool_calls 时可为 null） */
    private String content;

    /** tool 角色时的工具调用 ID */
    private String tool_call_id;

    /** assistant 角色发起工具调用时的列表（JSON 字符串，存 Redis 用） */
    private String tool_calls;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
