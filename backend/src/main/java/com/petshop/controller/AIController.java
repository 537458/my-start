package com.petshop.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.petshop.service.XunfeiAIService;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/ai")
public class AIController {

    private static final Logger log = LoggerFactory.getLogger(AIController.class);

    @Autowired
    private XunfeiAIService xunfeiAIService;

    // 历史对话存储（使用用户ID作为key）
    private final Map<String, List<JSONObject>> conversationHistory = new ConcurrentHashMap<>();
    
    // 最大历史对话轮数
    private static final int MAX_HISTORY_ROUNDS = 10;

    /**
     * 测试接口
     */
    @GetMapping("/test")
    public com.petshop.common.Response<String> test() {
        log.info("AI 测试接口被调用");
        return com.petshop.common.Response.success("AI 服务正常运行");
    }
    
    /**
     * AI 对话接口 - 流式输出版本
     */
    @PostMapping(value = "/chat-stream")
    public SseEmitter chatStream(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> params) {
        log.info("========== AI 流式对话接口被调用 ==========");
        log.info("收到参数：{}", params);
            
        String message = (String) params.get("message");
        String userId = (String) params.get("userId");
        
        if (userId == null || userId.trim().isEmpty()) {
            userId = "default_user";
        }
        
        // 确保 userId 是 final 的，用于 lambda 表达式
        final String finalUserId = userId;
        final String finalMessage = message;
    
        log.info("AI 对话请求，用户ID：{}, 消息：{}", userId, message);
            
        // 创建SSE发射器，超时时间120秒
        SseEmitter emitter = new SseEmitter(120000L);
        
        // 异步处理流式响应
        new Thread(() -> {
            try {
                log.info("开始调用 AI 服务");
                
                // 参数校验
                if (finalMessage == null || finalMessage.trim().isEmpty()) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("消息不能为空"));
                    emitter.complete();
                    return;
                }
                
                // 调用 XunfeiAIService 获取 AI 回复（使用服务中已有的流式处理逻辑）
                String aiResponse = xunfeiAIService.chat(finalUserId, finalMessage);
                
                // 保存历史对话到控制器层面（保持与非流式接口一致）
                saveConversationHistory(finalUserId, finalMessage, aiResponse);
                
                // 发送响应（由于 XunfeiAIService.chat 已处理流式，这里直接发送完整响应）
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(aiResponse));
                
                // 发送结束信号
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(aiResponse));
                
                emitter.complete();
                log.info("========== AI 对话完成 ==========");
                
            } catch (Exception e) {
                log.error("AI 对话失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("AI服务调用失败"));
                } catch (IOException ioEx) {
                    log.error("发送错误消息失败", ioEx);
                }
                // 不使用 completeWithError，避免Spring尝试序列化错误响应
                emitter.complete();
            }
        }).start();
        
        return emitter;
    }

    /**
     * AI 对话接口 - 非流式版本（兼容旧版）
     */
    @PostMapping("/chat")
    public com.petshop.common.Response<Map<String, String>> chat(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> params) {
        log.info("========== AI 对话接口被调用 ==========");
        log.info("收到参数：{}", params);
            
        String message = (String) params.get("message");
        String userId = (String) params.get("userId");
        
        if (userId == null || userId.trim().isEmpty()) {
            userId = "default_user";
        }
    
        log.info("AI 对话请求，用户ID：{}, 消息：{}", userId, message);
            
        if (message == null || message.trim().isEmpty()) {
            return com.petshop.common.Response.error(400, "消息不能为空");
        }
    
        try {
            log.info("准备调用 AI 服务");
                
            String response = xunfeiAIService.chat(userId, message);
                
            log.info("AI 服务返回：{}", response);

            Map<String, String> result = new HashMap<>();
            result.put("response", response);

            log.info("========== AI 对话接口调用完成 ==========");
            return com.petshop.common.Response.success(result);
        } catch (Exception e) {
            log.error("AI 对话失败", e);
            return com.petshop.common.Response.error(500, "AI 服务暂时不可用，请稍后重试");
        }
    }

    /**
     * 清空用户历史对话
     */
    @PostMapping("/clear-history")
    public com.petshop.common.Response<String> clearHistory(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> params) {
        log.info("========== 清空历史对话接口被调用 ==========");
        
        String userId = (String) params.get("userId");
        if (userId == null || userId.trim().isEmpty()) {
            userId = "default_user";
        }
        
        log.info("清空用户 {} 的历史对话", userId);
        
        try {
            conversationHistory.remove(userId);
            return com.petshop.common.Response.success("历史对话已清空");
        } catch (Exception e) {
            log.error("清空历史对话失败", e);
            return com.petshop.common.Response.error(500, "清空历史对话失败");
        }
    }

    /**
     * 构建请求体
     */
    private JSONObject buildRequestBody(String message, List<JSONObject> history) {
        JSONObject body = new JSONObject();
        body.put("model", "spark-x");
        
        JSONArray messages = new JSONArray();
        
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", "你是宠物乐园的 AI 助手，专门为用户提供宠物相关的咨询和建议。你可以回答关于宠物健康、饮食、护理、训练、产品推荐等方面的问题。回答要专业、友好、简洁，并且始终关注宠物的健康和幸福。");
        messages.add(systemMessage);
        
        messages.addAll(history);
        
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", message);
        messages.add(userMsg);
        
        body.put("messages", messages);
        body.put("stream", true);
        body.put("temperature", 0.7);
        body.put("top_k", 4);
        body.put("max_tokens", 4096);
        
        return body;
    }

    /**
     * 获取用户历史对话
     */
    private List<JSONObject> getConversationHistory(String userId) {
        List<JSONObject> history = conversationHistory.get(userId);
        if (history == null) {
            history = new ArrayList<>();
            conversationHistory.put(userId, history);
        }
        return history;
    }

    /**
     * 保存对话历史
     */
    private void saveConversationHistory(String userId, String userMessage, String aiResponse) {
        List<JSONObject> history = getConversationHistory(userId);
        
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        history.add(userMsg);
        
        JSONObject aiMsg = new JSONObject();
        aiMsg.put("role", "assistant");
        aiMsg.put("content", aiResponse);
        history.add(aiMsg);
        
        while (history.size() > MAX_HISTORY_ROUNDS * 2) {
            history.remove(0);
        }
        
        log.info("用户 {} 的历史对话已保存，当前共 {} 条", userId, history.size());
    }

    // 紧急指南接口
    @GetMapping("/emergency-guides")
    public com.petshop.common.Response<List<Map<String, Object>>> getEmergencyGuides() {
        log.info("获取宠物紧急指南");

        List<Map<String, Object>> emergencyGuides = new ArrayList<>();
        
        Map<String, Object> guide1 = new HashMap<>();
        guide1.put("id", 1);
        guide1.put("title", "食物中毒");
        guide1.put("content", "如果宠物误食有毒食物，立即联系宠物医院，并携带可疑食物样本。");
        guide1.put("image", "https://via.placeholder.com/300");
        guide1.put("priority", "high");
        emergencyGuides.add(guide1);

        Map<String, Object> guide2 = new HashMap<>();
        guide2.put("id", 2);
        guide2.put("title", "外伤处理");
        guide2.put("content", "对于轻微外伤，可先进行简单消毒处理，严重外伤应立即送医。");
        guide2.put("image", "https://via.placeholder.com/300");
        guide2.put("priority", "high");
        emergencyGuides.add(guide2);

        Map<String, Object> guide3 = new HashMap<>();
        guide3.put("id", 3);
        guide3.put("title", "呼吸困难");
        guide3.put("content", "如果宠物出现呼吸困难，保持安静并立即送医。");
        guide3.put("image", "https://via.placeholder.com/300");
        guide3.put("priority", "critical");
        emergencyGuides.add(guide3);

        return com.petshop.common.Response.success(emergencyGuides);
    }
}
