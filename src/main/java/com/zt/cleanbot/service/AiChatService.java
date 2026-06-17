package com.zt.cleanbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zt.cleanbot.dto.ai.AiChatRequest;
import com.zt.cleanbot.dto.ai.AiChatResponse;
import com.zt.cleanbot.dto.ai.ChatMessage;
import com.zt.cleanbot.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * AI 对话服务
 * 负责与 DeepSeek API 交互，管理多轮对话上下文（存 Redis）
 */
@Service
@Slf4j
public class AiChatService {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String model;

    @Value("${deepseek.max-history-rounds:10}")
    private int maxHistoryRounds;

    /** Redis key 前缀，避免与设备数据冲突 */
    private static final String SESSION_KEY_PREFIX = "ai:session:";
    /** 会话过期时间：2小时 */
    private static final long SESSION_TTL_SECONDS = 7200;

    /** System prompt：告诉 AI 它的角色和当前时间 */
    private static final String SYSTEM_PROMPT_TEMPLATE =
        "你是一个工业清洁机器人智能助手，专门帮助操作人员管理、监控和控制清洁机器人车队。\n" +
        "当前时间：%s\n\n" +
        "你的能力：\n" +
        "1. 实时查询设备状态（电量、位置、速度、故障码）\n" +
        "2. 查询历史任务记录（清洁面积、效率统计）\n" +
        "3. 查询车辆行驶历史轨迹和电量变化\n" +
        "4. 列出所有已知设备\n" +
        "5. 控制设备（发送 AUTO启动/STOP停止/RESET复位/CONT循环/MAN手动 指令）\n\n" +
        "设备ID格式说明：-D01XXXXXXX 为清洁车，-D12XXXXXXX 为接驳车。\n\n" +
        "控制规则：\n" +
        "- 仅在用户明确要求控制操作时（如\"启动\"、\"停止\"、\"复位\"）才调用 control_device 工具\n" +
        "- 如用户未指定设备ID，先调用 list_devices 获取设备列表，让用户确认目标设备\n" +
        "- 控制前可先查询设备状态，确认设备在线\n" +
        "- 回答时简洁直接，数字保留合理精度，遇到故障码主动告知可能原因。";

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private AiToolExecutor toolExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * 处理一次对话请求（支持多轮上下文 + Function Calling）
     */
    public AiChatResponse chat(AiChatRequest chatRequest) {
        // 生成或使用已有 sessionId
        String sessionId = (chatRequest.getSessionId() != null && !chatRequest.getSessionId().isEmpty())
                ? chatRequest.getSessionId()
                : UUID.randomUUID().toString();

        log.info("[AiChat] sessionId={}, 用户消息={}", sessionId, chatRequest.getMessage());

        try {
            // 1. 加载历史上下文
            List<ChatMessage> history = loadHistory(sessionId);

            // 2. 追加用户消息
            history.add(new ChatMessage("user", chatRequest.getMessage()));

            // 3. 构建请求并调用 DeepSeek（含 Function Calling 循环）
            String reply = callDeepSeekWithTools(history);

            // 4. 追加 AI 回复到历史，并截断超长上下文
            history.add(new ChatMessage("assistant", reply));
            trimHistory(history);

            // 5. 持久化更新后的上下文到 Redis
            saveHistory(sessionId, history);

            return AiChatResponse.ok(sessionId, reply);

        } catch (Exception e) {
            log.error("[AiChat] 对话处理异常, sessionId={}", sessionId, e);
            return AiChatResponse.error(sessionId, e.getMessage());
        }
    }

    /**
     * 清除会话上下文
     */
    public void clearSession(String sessionId) {
        redisUtil.delete(SESSION_KEY_PREFIX + sessionId);
        log.info("[AiChat] 已清除会话: {}", sessionId);
    }

    // ===== 核心：调用 DeepSeek + Function Calling 循环 =====

    private String callDeepSeekWithTools(List<ChatMessage> history) throws Exception {
        // 最多循环 5 次（防止工具调用死循环）
        for (int round = 0; round < 5; round++) {
            String requestBody = buildRequestBody(history);
            log.debug("[AiChat] DeepSeek 请求 (round={}): {}", round, requestBody);

            String responseJson = doHttpPost(requestBody);
            log.debug("[AiChat] DeepSeek 响应: {}", responseJson);

            JsonNode root = objectMapper.readTree(responseJson);

            // 检查 API 错误
            if (root.has("error")) {
                String errMsg = root.path("error").path("message").asText("DeepSeek API 错误");
                throw new RuntimeException("DeepSeek API 错误: " + errMsg);
            }

            JsonNode choice = root.path("choices").path(0);
            JsonNode message = choice.path("message");
            String finishReason = choice.path("finish_reason").asText("");

            // 如果 AI 直接给出最终回答
            if ("stop".equals(finishReason)) {
                return message.path("content").asText();
            }

            // 如果 AI 要调用工具
            if ("tool_calls".equals(finishReason) && message.has("tool_calls")) {
                // 将 assistant 的 tool_calls 消息加入历史（保留原始 JSON）
                ChatMessage assistantMsg = new ChatMessage("assistant", null);
                assistantMsg.setTool_calls(message.get("tool_calls").toString());
                history.add(assistantMsg);

                // 依次执行每个工具调用，将结果加入历史
                for (JsonNode toolCall : message.get("tool_calls")) {
                    String toolCallId = toolCall.path("id").asText();
                    String toolName = toolCall.path("function").path("name").asText();
                    String toolArgs = toolCall.path("function").path("arguments").asText();

                    log.info("[AiChat] 调用工具: name={}, args={}", toolName, toolArgs);
                    String toolResult = toolExecutor.execute(toolName, toolArgs);
                    log.info("[AiChat] 工具结果: {}", toolResult);

                    ChatMessage toolMsg = new ChatMessage("tool", toolResult);
                    toolMsg.setTool_call_id(toolCallId);
                    history.add(toolMsg);
                }
                // 继续下一轮，让 AI 根据工具结果给出最终回答
                continue;
            }

            // 其他情况直接返回内容
            String content = message.path("content").asText("");
            if (!content.isEmpty()) {
                return content;
            }
        }

        return "处理超时，请重新提问。";
    }

    // ===== 构建 DeepSeek 请求体 =====

    private String buildRequestBody(List<ChatMessage> history) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);

        // 构建消息数组
        ArrayNode messages = body.putArray("messages");

        // 第一条：system prompt（含当前时间）
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", String.format(SYSTEM_PROMPT_TEMPLATE,
                new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())));

        // 历史消息
        for (ChatMessage msg : history) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", msg.getRole());

            if ("tool".equals(msg.getRole())) {
                // tool 角色消息
                msgNode.put("content", msg.getContent());
                msgNode.put("tool_call_id", msg.getTool_call_id());
            } else if ("assistant".equals(msg.getRole()) && msg.getTool_calls() != null) {
                // assistant 发起工具调用时 content 可为 null
                if (msg.getContent() != null) {
                    msgNode.put("content", msg.getContent());
                } else {
                    msgNode.putNull("content");
                }
                // 将 tool_calls JSON 字符串解析回 JsonNode 放入
                msgNode.set("tool_calls", objectMapper.readTree(msg.getTool_calls()));
            } else {
                msgNode.put("content", msg.getContent() != null ? msg.getContent() : "");
            }
        }

        // 工具定义
        JsonNode toolsNode = objectMapper.readTree(AiToolExecutor.getToolsDefinitionJson());
        body.set("tools", toolsNode);
        body.put("tool_choice", "auto");

        return objectMapper.writeValueAsString(body);
    }

    // ===== HTTP 调用 =====

    private String doHttpPost(String requestBody) throws Exception {
        RequestBody body = RequestBody.create(requestBody, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() == null) {
                throw new RuntimeException("DeepSeek API 返回空响应");
            }
            String responseStr = response.body().string();
            if (!response.isSuccessful()) {
                log.error("[AiChat] DeepSeek HTTP错误 {}: {}", response.code(), responseStr);
                throw new RuntimeException("DeepSeek API HTTP " + response.code() + ": " + responseStr);
            }
            return responseStr;
        }
    }

    // ===== Redis 上下文管理 =====

    @SuppressWarnings("unchecked")
    private List<ChatMessage> loadHistory(String sessionId) {
        String key = SESSION_KEY_PREFIX + sessionId;
        Object raw = redisUtil.get(key);
        if (raw == null) {
            return new ArrayList<>();
        }
        try {
            // Redis 存的是 JSON 字符串
            List<Map<String, String>> rawList = objectMapper.readValue(raw.toString(), List.class);
            List<ChatMessage> history = new ArrayList<>();
            for (Map<String, String> m : rawList) {
                ChatMessage msg = new ChatMessage();
                msg.setRole(m.get("role"));
                msg.setContent(m.get("content"));
                msg.setTool_call_id(m.get("tool_call_id"));
                msg.setTool_calls(m.get("tool_calls"));
                history.add(msg);
            }
            return history;
        } catch (Exception e) {
            log.warn("[AiChat] 历史上下文解析失败，重置会话: {}", sessionId);
            return new ArrayList<>();
        }
    }

    private void saveHistory(String sessionId, List<ChatMessage> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            redisUtil.set(SESSION_KEY_PREFIX + sessionId, json, SESSION_TTL_SECONDS);
        } catch (Exception e) {
            log.error("[AiChat] 保存会话历史失败: {}", sessionId, e);
        }
    }

    /**
     * 超过最大轮数时，保留最近 N 轮（每轮 = user + assistant 各一条）
     * 注意：tool 消息也要一起保留，否则 DeepSeek 会报错
     */
    private void trimHistory(List<ChatMessage> history) {
        int maxMessages = maxHistoryRounds * 2 + 10; // +10 为 tool 消息预留空间
        while (history.size() > maxMessages) {
            history.remove(0);
        }
    }
}
