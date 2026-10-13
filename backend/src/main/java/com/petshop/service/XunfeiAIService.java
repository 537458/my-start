package com.petshop.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class XunfeiAIService {

    private static final Logger log = LoggerFactory.getLogger(XunfeiAIService.class);

    @Value("${xunfei.appid:}")
    private String appId;

    @Value("${xunfei.apikey:}")
    private String apiKey;

    @Value("${xunfei.apisecret:}")
    private String apiSecret;

    // API Password (用于 HTTP 鉴权)，必须通过 XUNFEI_APIPASSWORD 环境变量注入
    @Value("${xunfei.apipassword:}")
    private String apiPassword;

    private String getApiPassword() {
        return apiPassword == null ? "" : apiPassword.trim();
    }

    // 讯飞星火大模型 API 地址（HTTP 接口）
    private static final String SPARK_URL = "https://spark-api-open.xf-yun.com/x2/chat/completions";

    // 历史对话存储（使用用户ID作为key）
    private final Map<String, List<JSONObject>> conversationHistory = new ConcurrentHashMap<>();
    
    // 最大历史对话轮数
    private static final int MAX_HISTORY_ROUNDS = 10;

    /**
     * 调用讯飞星火 AI 进行对话（使用流式响应）
     * @param userId 用户ID（用于保存历史对话）
     * @param userMessage 用户消息
     * @return AI 回复
     */
    public String chat(String userId, String userMessage) {
        try {
            // 检查配置
            if (apiKey == null || apiKey.trim().isEmpty()
                    || apiSecret == null || apiSecret.trim().isEmpty()
                    || getApiPassword().isEmpty()) {
                log.warn("讯飞星火 API 配置未设置，使用模拟回复");
                return getMockResponse(userMessage);
            }

            log.info("========== 开始调用讯飞星火 AI (流式) ==========");
            log.info("用户ID：{}, 消息：{}", userId, userMessage);
            
            // 获取历史对话
            List<JSONObject> history = getConversationHistory(userId);
            
            // 构建请求体（包含历史对话）
            JSONObject requestBody = buildRequestBodyWithHistory(userMessage, history);
            log.info("请求体长度：{}", requestBody.toJSONString().length());
            
            // 创建 HTTP 客户端（支持流式响应）
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)  // 流式响应需要更长超时
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            RequestBody body = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody.toJSONString()
            );

            Request request = new Request.Builder()
                    .url(SPARK_URL)
                    .addHeader("Authorization", "Bearer " + getApiPassword())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "text/event-stream")  // 声明接收流式响应
                    .post(body)
                    .build();

            log.info("发送流式请求到：{}", SPARK_URL);
            
            // 执行流式请求
            StringBuilder fullResponse = new StringBuilder();
            try (Response response = client.newCall(request).execute()) {
                log.info("HTTP 响应码：{}", response.code());
                
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "null";
                    log.error("请求失败：code={}, body={}", response.code(), errorBody);
                    return getMockResponse(userMessage);
                }

                // 处理流式响应
                InputStream inputStream = response.body().byteStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    // 过滤空行
                    if (line.isEmpty()) continue;
                    
                    // 解析 SSE 格式
                    if (line.startsWith("data: ")) {
                        String jsonStr = line.substring(6);
                        
                        try {
                            JSONObject jsonResponse = JSON.parseObject(jsonStr);
                            int code = jsonResponse.getIntValue("code");
                            
                            if (code != 0) {
                                log.error("API 错误：code={}, message={}", 
                                    code, jsonResponse.getString("message"));
                                break;
                            }

                            JSONArray choices = jsonResponse.getJSONArray("choices");
                            if (choices != null && choices.size() > 0) {
                                JSONObject choice = choices.getJSONObject(0);
                                String delta = choice.getString("delta");
                                
                                if (delta != null && !delta.isEmpty()) {
                                    JSONObject deltaObj = JSON.parseObject(delta);
                                    String content = deltaObj.getString("content");
                                    if (content != null && !content.isEmpty()) {
                                        fullResponse.append(content);
                                        log.debug("收到流式数据：{}", content);
                                    }
                                }
                                
                                // 检查是否结束
                                String finishReason = choice.getString("finish_reason");
                                if ("stop".equals(finishReason)) {
                                    log.info("流式响应结束");
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            // 可能是 incomplete JSON，继续读取
                            log.debug("解析单行JSON失败（可能是不完整数据）: {}", line);
                        }
                    }
                }
            }

            String finalResponse = fullResponse.toString().trim();
            log.info("AI 回复内容长度：{}", finalResponse.length());
            log.info("AI 回复内容：{}", finalResponse);
            
            // 保存对话历史
            saveConversationHistory(userId, userMessage, finalResponse);
            
            log.info("========== AI 调用完成 ==========");
            return finalResponse.isEmpty() ? getMockResponse(userMessage) : finalResponse;

        } catch (Exception e) {
            log.error("调用讯飞星火 AI 失败", e);
            return getMockResponse(userMessage);
        }
    }

    /**
     * 兼容旧版本的无用户ID调用
     */
    public String chat(String userMessage) {
        return chat("default_user", userMessage);
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
        
        // 添加用户消息
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        history.add(userMsg);
        
        // 添加AI回复
        JSONObject aiMsg = new JSONObject();
        aiMsg.put("role", "assistant");
        aiMsg.put("content", aiResponse);
        history.add(aiMsg);
        
        // 保持历史对话在最大轮数以内
        while (history.size() > MAX_HISTORY_ROUNDS * 2) {  // 每轮包含用户和AI消息
            history.remove(0);
        }
        
        log.info("用户 {} 的历史对话已保存，当前共 {} 条", userId, history.size());
    }

    /**
     * 清空用户历史对话
     */
    public void clearHistory(String userId) {
        conversationHistory.remove(userId);
        log.info("用户 {} 的历史对话已清空", userId);
    }

    /**
     * 构建请求体（包含历史对话）
     */
    private JSONObject buildRequestBodyWithHistory(String userMessage, List<JSONObject> history) {
        JSONObject body = new JSONObject();
        
        // model 参数 - 使用 spark-x 模型
        body.put("model", "spark-x");
        
        // messages 参数
        JSONArray messages = new JSONArray();
        
        // 系统角色设定
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");
        String systemContent = "你是宠物用品商城的 AI 助手，专门为用户提供宠物用品相关的咨询和建议。" +
                "你可以回答关于宠物用品、饮食、产品推荐等方面的问题。" +
                "回答要专业、友好、简洁，并且始终关注宠物用品的推荐。" +
                "用户问到的东西你都要答出来，但是不要答非所问";
        systemMessage.put("content", systemContent);
        messages.add(systemMessage);
        
        // 添加历史对话
        messages.addAll(history);
        
        // 添加当前用户消息
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);
        
        body.put("messages", messages);
        
        // 流式响应配置
        body.put("stream", true);  // 启用流式响应
        body.put("temperature", 0.7);
        body.put("top_k", 4);
        body.put("max_tokens", 4096);
        
        log.info("构建请求体完成，消息数量：{}", messages.size());
        return body;
    }

    /**
     * 模拟回复（当API未配置时使用）
     */
    private String getMockResponse(String userMessage) {
        String message = userMessage.toLowerCase();
        
        // 针对猫咪饮食的具体问题
        if (message.contains("猫") && (message.contains("吃") || message.contains("食") || message.contains("粮"))) {
            if (message.contains("一岁") || message.contains("1岁") || message.contains("幼猫") || message.contains("小猫")) {
                return "一岁的猫咪正处于成长期，建议选择高蛋白、易消化的幼猫粮或成猫幼年期专用粮。每天喂食3-4次，控制食量避免肥胖。可以适量补充猫用营养品，但要注意不要过量。避免喂食人类食物，特别是洋葱、大蒜、巧克力等有毒食物。";
            }
            return "猫咪是纯肉食动物，建议选择高蛋白低碳水的猫粮。根据年龄选择幼猫粮、成猫粮或老年猫粮。可以适当添加猫罐头、冻干等作为辅食，但不要替代主食。注意保证充足的新鲜饮水。";
        }
        
        // 针对狗狗饮食的具体问题
        if (message.contains("狗") && (message.contains("吃") || message.contains("食") || message.contains("粮"))) {
            if (message.contains("一岁") || message.contains("1岁") || message.contains("幼犬")) {
                return "一岁的狗狗已经进入青年期，可以开始喂食成犬粮。选择营养均衡的品牌狗粮，每天喂食2次。可以适量添加蔬菜、水果作为补充，但不要喂洋葱、葡萄、巧克力等有毒食物。注意控制体重，避免过度喂食。";
            }
            return "狗狗饮食要选择营养均衡的狗粮，根据体型、年龄、活动量选择合适的配方。小型犬注意选择颗粒较小的狗粮，大型犬要注意关节保护配方。定时定量喂食，保证充足饮水。";
        }
        
        // 通用健康问题
        if (message.contains("健康") || message.contains("病") || message.contains("症状")) {
            return "根据您的描述，建议密切观察宠物的精神状态和食欲。如果症状持续或加重，请及时带宠物去正规宠物医院就诊。平时要注意定期体检和疫苗接种。";
        }
        
        // 通用饮食问题
        if (message.contains("吃") || message.contains("食") || message.contains("粮") || message.contains("饭")) {
            return "宠物的饮食要根据年龄、体重、品种来选择。建议选择正规品牌的宠物粮，定时定量喂食，避免给宠物吃人类的食物，特别是巧克力、洋葱、葡萄等对宠物有毒的食物。";
        }
        
        // 护理问题
        if (message.contains("洗澡") || message.contains("护理") || message.contains("清洁")) {
            return "宠物护理包括定期洗澡、梳毛、剪指甲、清洁耳朵等。洗澡频率根据宠物品种而定，一般狗狗2-4周一次，猫咪可以适当延长。使用宠物专用洗护产品，洗完后要彻底吹干。";
        }
        
        // 训练问题
        if (message.contains("训练") || message.contains("教") || message.contains("习惯")) {
            return "训练宠物需要耐心和一致性。建议使用正向激励的方法，当宠物表现好时给予奖励。训练时间不宜过长，每次10-15分钟，每天多次。从简单的指令开始，如坐下、握手等。";
        }
        
        // 疫苗问题
        if (message.contains("疫苗") || message.contains("打针") || message.contains("预防")) {
            return "宠物疫苗接种非常重要。狗狗通常需要接种犬瘟热、细小病毒、传染性肝炎等疫苗，猫咪需要接种猫瘟、猫鼻支等疫苗。建议咨询兽医制定合适的疫苗接种计划。";
        }
        
        // 产品推荐相关
        if (message.contains("推荐") || message.contains("买") || message.contains("用品") || message.contains("玩具")) {
            return "我们商城有丰富的宠物用品，包括优质宠物粮、玩具、窝垫、牵引绳等。根据您的宠物类型和需求，推荐选择正规品牌产品。如有具体需求，可以告诉我，我来帮您推荐！";
        }
        
        // 日常问候
        if (message.contains("你好") || message.contains("您好") || message.contains("嗨") || message.contains("hello")) {
            return "您好！我是宠物用品小助手，有什么关于宠物用品的问题尽管问我吧！";
        }
        
        // 默认回复
        return "请问您有什么关于宠物的问题呢？我可以帮您解答饮食、健康、护理等方面的问题哦~";
    }
}
