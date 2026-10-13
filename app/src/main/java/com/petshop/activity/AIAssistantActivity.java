package com.petshop.activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.petshop.R;
import com.petshop.adapter.MessageAdapter;
import com.petshop.api.ApiClient;
import com.petshop.api.ApiService;
import com.petshop.api.NetworkCallback;
import com.petshop.model.Message;
import com.petshop.util.AuthUtil;
import com.petshop.util.SecurePreferences;
import com.petshop.util.ToastUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
public class AIAssistantActivity extends AppCompatActivity {
    private static final String TAG = "AIAssistantActivity";
    private ImageView ivBack, ivSend;
    private EditText etMessage;
    private RecyclerView rvMessages;
    private MessageAdapter messageAdapter;
    private List<Message> messageList;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: 开始创建AI助手Activity");
        
        if (!AuthUtil.checkLoginWithPddStyle(this)) {
            finish();
            return;
        }
        
        try {
            setContentView(R.layout.activity_ai_assistant);
        } catch (Exception e) {
            Log.e(TAG, "布局加载失败", e);
            Toast.makeText(this, R.string.error_operation_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            initView();
        } catch (Exception e) {
            Log.e(TAG, "视图初始化失败", e);
            Toast.makeText(this, R.string.error_operation_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        try {
            initListeners();
        } catch (Exception e) {
            Log.e(TAG, "监听器设置失败", e);
            Toast.makeText(this, R.string.error_operation_failed, Toast.LENGTH_SHORT).show();
        }
        
        Log.d(TAG, "onCreate: AI助手Activity创建完成");
    }

    private void initView() {
        Log.d(TAG, "initView: 开始初始化视图");
        
        try {
            ivBack = findViewById(R.id.iv_back);
            Log.d(TAG, "initView: ivBack = " + (ivBack != null ? "成功" : "失败"));
        } catch (Exception e) {
            Log.e(TAG, "initView: ivBack初始化失败", e);
        }
        
        try {
            ivSend = findViewById(R.id.iv_send);
            Log.d(TAG, "initView: ivSend = " + (ivSend != null ? "成功" : "失败"));
        } catch (Exception e) {
            Log.e(TAG, "initView: ivSend初始化失败", e);
        }
        
        try {
            etMessage = findViewById(R.id.et_message);
            Log.d(TAG, "initView: etMessage = " + (etMessage != null ? "成功" : "失败"));
        } catch (Exception e) {
            Log.e(TAG, "initView: etMessage初始化失败", e);
        }
        
        try {
            rvMessages = findViewById(R.id.rv_messages);
            Log.d(TAG, "initView: rvMessages = " + (rvMessages != null ? "成功" : "失败"));
        } catch (Exception e) {
            Log.e(TAG, "initView: rvMessages初始化失败", e);
        }

        // 先尝试加载历史对话记录
        messageList = loadChatHistory();
        Log.d(TAG, "initView: messageList加载成功，记录数=" + messageList.size());
        
        // 如果没有历史记录，添加欢迎消息
        if (messageList.isEmpty()) {
            try {
                messageList.add(new Message(Message.TYPE_AI, "你好！我是宠物乐园的AI助手，有什么可以帮到你的吗？"));
                Log.d(TAG, "initView: 欢迎消息添加成功");
            } catch (Exception e) {
                Log.e(TAG, "initView: 欢迎消息添加失败", e);
            }
        }
        
        try {
            messageAdapter = new MessageAdapter(this, messageList);
            Log.d(TAG, "initView: MessageAdapter创建成功");
        } catch (Exception e) {
            Log.e(TAG, "initView: MessageAdapter创建失败", e);
        }
        
        try {
            rvMessages.setLayoutManager(new LinearLayoutManager(this));
            rvMessages.setAdapter(messageAdapter);
            Log.d(TAG, "initView: RecyclerView设置成功");
        } catch (Exception e) {
            Log.e(TAG, "initView: RecyclerView设置失败", e);
        }
    }

    private void initListeners() {
        Log.d(TAG, "initListeners: 开始设置监听器");
        
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "initListeners: 返回按钮被点击");
                finish();
            }
        });
        
        ivSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "initListeners: 发送按钮被点击");
                sendMessage();
            }
        });
        
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            Log.d(TAG, "initListeners: 软键盘发送按钮被点击");
            sendMessage();
            return true;
        });
        
        Log.d(TAG, "initListeners: 监听器设置完成");
    }

    private void sendMessage() {
        String message = etMessage.getText().toString().trim();
        Log.d(TAG, "sendMessage: 用户输入消息 = " + message);
        
        if (message.isEmpty()) {
            ToastUtil.show(this, "请输入消息内容");
            return;
        }
        
        // 添加用户消息
        try {
            messageList.add(new Message(Message.TYPE_USER, message));
            messageAdapter.notifyDataSetChanged();
            rvMessages.scrollToPosition(messageList.size() - 1);
            etMessage.setText("");
            Log.d(TAG, "sendMessage: 用户消息添加成功");
        } catch (Exception e) {
            Log.e(TAG, "sendMessage: 用户消息添加失败", e);
        }
        
        // 调用AI助手
        callAIAssistant(message);
    }

    private void callAIAssistant(String question) {
        Log.d(TAG, "callAIAssistant: 调用 AI 助手（流式），问题 = " + question);
        
        // 创建流式请求体
        JSONObject requestBody = new JSONObject();
        try {
            requestBody.put("message", question);
            requestBody.put("userId", getUserId());
        } catch (JSONException e) {
            Log.e(TAG, "callAIAssistant: 创建请求体失败", e);
            simulateAIResponse(question);
            return;
        }
        
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"),
                requestBody.toString()
        );
        
        // 添加AI消息占位符
        Message aiMessage = new Message(Message.TYPE_AI, "");
        messageList.add(aiMessage);
        int aiMessageIndex = messageList.size() - 1;
        
        final StringBuilder fullResponse = new StringBuilder();
        final int finalAiMessageIndex = aiMessageIndex;
        
        // 获取基础URL
        String baseUrl = ApiClient.getCurrentBaseUrl(this);
        if (baseUrl == null) {
            Log.e(TAG, "callAIAssistant: 无法获取API地址");
            simulateAIResponse(question);
            return;
        }
        
        // 构建完整的请求URL
        String fullUrl = baseUrl + "ai/chat-stream";
        Log.d(TAG, "callAIAssistant: 请求URL = " + fullUrl);
        
        // 构建请求
        Request request = new Request.Builder()
                .url(fullUrl)
                .header("Accept", "text/event-stream")
                .header("Content-Type", "application/json")
                .post(body)
                .build();
        
        // 使用OkHttpClient直接发送请求
        ApiClient.getInstance(this).getOkHttpClient().newCall(request)
                .enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "callAIAssistant: 网络请求失败", e);
                        runOnUiThread(() -> {
                            messageList.remove(finalAiMessageIndex);
                            simulateAIResponse(question);
                        });
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (!response.isSuccessful()) {
                            Log.e(TAG, "callAIAssistant: HTTP请求失败，状态码=" + response.code());
                            runOnUiThread(() -> {
                                messageList.remove(finalAiMessageIndex);
                                simulateAIResponse(question);
                            });
                            return;
                        }
                        
                        ResponseBody responseBody = response.body();
                        if (responseBody == null) {
                            Log.e(TAG, "callAIAssistant: 响应体为空");
                            runOnUiThread(() -> {
                                messageList.remove(finalAiMessageIndex);
                                simulateAIResponse(question);
                            });
                            return;
                        }
                        
                        // 处理流式响应（SSE格式）
                        InputStream inputStream = responseBody.byteStream();
                        BufferedReader reader = new BufferedReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                        
                        String line;
                        String currentEvent = "";
                        int lineCount = 0;
                        while ((line = reader.readLine()) != null) {
                            lineCount++;
                            Log.d(TAG, "callAIAssistant: 收到第 " + lineCount + " 行数据: [" + line + "]");
                            
                            if (line.isEmpty()) {
                                Log.d(TAG, "callAIAssistant: 跳过空行");
                                continue;
                            }
                            
                            // 解析SSE事件类型
                            if (line.startsWith("event:")) {
                                currentEvent = line.substring(6).trim();
                                Log.d(TAG, "callAIAssistant: 收到事件类型 - " + currentEvent);
                            }
                            // 解析SSE数据
                            else if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                Log.d(TAG, "callAIAssistant: 收到数据 - [" + data + "]");
                                Log.d(TAG, "callAIAssistant: 当前事件类型 - [" + currentEvent + "]");
                                
                                // 根据事件类型处理数据
                                if ("message".equals(currentEvent)) {
                                    // 只有message事件才追加数据，避免complete事件重复
                                    fullResponse.append(data);
                                    Log.d(TAG, "callAIAssistant: 当前fullResponse内容 - [" + fullResponse.toString() + "]");
                                    
                                    // 更新UI显示流式内容
                                    runOnUiThread(() -> {
                                        aiMessage.setContent(fullResponse.toString());
                                        messageAdapter.notifyItemChanged(finalAiMessageIndex);
                                        rvMessages.scrollToPosition(messageList.size() - 1);
                                    });
                                } else if ("complete".equals(currentEvent)) {
                                    // complete事件只标记完成，不追加数据（避免重复）
                                    Log.d(TAG, "callAIAssistant: SSE响应完成");
                                } else if ("error".equals(currentEvent)) {
                                    Log.e(TAG, "callAIAssistant: SSE错误 - " + data);
                                    runOnUiThread(() -> {
                                        messageList.remove(finalAiMessageIndex);
                                        simulateAIResponse(question);
                                    });
                                    return;
                                } else {
                                    // 没有事件类型或未知事件类型，直接当作消息数据处理
                                    Log.d(TAG, "callAIAssistant: 未知事件类型，直接处理数据");
                                    fullResponse.append(data);
                                    runOnUiThread(() -> {
                                        aiMessage.setContent(fullResponse.toString());
                                        messageAdapter.notifyItemChanged(finalAiMessageIndex);
                                        rvMessages.scrollToPosition(messageList.size() - 1);
                                    });
                                }
                            } else {
                                // 其他类型的行，也尝试作为数据处理
                                Log.d(TAG, "callAIAssistant: 非标准SSE行，尝试作为数据处理: " + line);
                                if (!line.startsWith("event:") && !line.startsWith("data:")) {
                                    fullResponse.append(line);
                                    runOnUiThread(() -> {
                                        aiMessage.setContent(fullResponse.toString());
                                        messageAdapter.notifyItemChanged(finalAiMessageIndex);
                                        rvMessages.scrollToPosition(messageList.size() - 1);
                                    });
                                }
                            }
                        }
                        
                        reader.close();
                        inputStream.close();
                        
                        // 检查是否收到完整响应
                        if (fullResponse.length() == 0) {
                            Log.e(TAG, "callAIAssistant: 未收到AI响应内容");
                            runOnUiThread(() -> {
                                messageList.remove(finalAiMessageIndex);
                                simulateAIResponse(question);
                            });
                        } else {
                            Log.d(TAG, "callAIAssistant: AI流式回复完成，内容长度=" + fullResponse.length());
                        }
                    }
                });
    }
    
    private String getUserId() {
        try {
            // 从SecurePreferences获取用户ID（登录时保存的方式）
            int userId = SecurePreferences.getUserId(this);
            if (userId != -1) {
                return String.valueOf(userId);
            }
            // 如果SecurePreferences中没有，尝试从普通SharedPreferences获取
            SharedPreferences preferences = getSharedPreferences("petshop", MODE_PRIVATE);
            String userIdStr = preferences.getString("userId", null);
            if (userIdStr != null && !userIdStr.isEmpty()) {
                return userIdStr;
            }
            // 如果都没有，使用默认用户ID
            return "default_user";
        } catch (Exception e) {
            Log.e(TAG, "getUserId: 获取用户ID失败", e);
            return "default_user";
        }
    }

    /**
     * 加载历史对话记录
     */
    private List<Message> loadChatHistory() {
        try {
            SharedPreferences preferences = getSharedPreferences("petshop_chat", MODE_PRIVATE);
            String userId = getUserId();
            String historyJson = preferences.getString("chat_history_" + userId, null);
            
            if (historyJson != null && !historyJson.isEmpty()) {
                Gson gson = new Gson();
                Type type = new TypeToken<List<Message>>() {}.getType();
                List<Message> history = gson.fromJson(historyJson, type);
                Log.d(TAG, "loadChatHistory: 成功加载历史记录，数量=" + (history != null ? history.size() : 0));
                return history != null ? history : new ArrayList<>();
            }
        } catch (Exception e) {
            Log.e(TAG, "loadChatHistory: 加载历史记录失败", e);
        }
        return new ArrayList<>();
    }

    /**
     * 保存对话记录
     */
    private void saveChatHistory() {
        try {
            SharedPreferences preferences = getSharedPreferences("petshop_chat", MODE_PRIVATE);
            String userId = getUserId();
            Gson gson = new Gson();
            String historyJson = gson.toJson(messageList);
            preferences.edit().putString("chat_history_" + userId, historyJson).apply();
            Log.d(TAG, "saveChatHistory: 成功保存历史记录，数量=" + messageList.size());
        } catch (Exception e) {
            Log.e(TAG, "saveChatHistory: 保存历史记录失败", e);
        }
    }

    /**
     * 清空对话记录
     */
    private void clearChatHistory() {
        try {
            SharedPreferences preferences = getSharedPreferences("petshop_chat", MODE_PRIVATE);
            String userId = getUserId();
            preferences.edit().remove("chat_history_" + userId).apply();
            messageList.clear();
            messageList.add(new Message(Message.TYPE_AI, "你好！我是宠物乐园的AI助手，有什么可以帮到你的吗？"));
            messageAdapter.notifyDataSetChanged();
            Log.d(TAG, "clearChatHistory: 成功清空历史记录");
        } catch (Exception e) {
            Log.e(TAG, "clearChatHistory: 清空历史记录失败", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 保存对话记录
        saveChatHistory();
        Log.d(TAG, "onDestroy: 已保存对话记录");
    }

    private void simulateAIResponse(String userMessage) {
        Log.d(TAG, "simulateAIResponse: 开始生成AI回复，用户消息 = " + userMessage);
        
        // 智能回复逻辑（降级方案）
        String response = "";
        if (userMessage.contains("健康") || userMessage.contains("建议") || userMessage.contains("体检")) {
            response = "💡 健康建议：\n\n" +
                    "1. 定期体检：建议每 6 个月带宠物去宠物医院进行一次全面体检\n" +
                    "2. 合理饮食：根据宠物的年龄、体重选择合适的宠物食品\n" +
                    "3. 适量运动：每天保证宠物有足够的运动量\n\n" +
                    "如果您需要更详细的建议，请咨询专业兽医。";
        } else if (userMessage.contains("产品") || userMessage.contains("推荐") || userMessage.contains("购买")) {
            response = "🛍️ 产品推荐：\n\n" +
                    "根据您的宠物需求，我推荐：\n" +
                    "1. 高品质宠物粮 - 提供全面营养\n" +
                    "2. 互动玩具 - 增加运动量和乐趣\n" +
                    "3. 舒适窝垫 - 提供良好的休息环境\n\n" +
                    "您可以在我们的商城浏览更多优质产品！";
        } else if (userMessage.contains("护理") || userMessage.contains("洗澡") || userMessage.contains("梳理")) {
            response = "🛁 日常护理指南：\n\n" +
                    "1. 毛发梳理：定期梳理毛发，预防打结\n" +
                    "2. 洗澡护理：使用专用沐浴露，水温适中\n" +
                    "3. 指甲修剪：定期修剪指甲，避免过长\n\n" +
                    "保持宠物清洁卫生非常重要哦！";
        } else if (userMessage.contains("训练") || userMessage.contains("行为")) {
            response = "🎾 训练建议：\n\n" +
                    "1. 耐心教导：训练需要时间和耐心\n" +
                    "2. 正向激励：使用奖励机制效果更好\n" +
                    "3. 一致性：训练方法要保持一致\n\n" +
                    "良好的训练能让宠物更听话哦！";
        } else if (userMessage.contains("领养") || userMessage.contains("救助")) {
            response = "� 领养信息：\n\n" +
                    "我们支持宠物领养，欢迎关注我们的领养活动。\n" +
                    "领养代替购买，给流浪宠物一个温暖的家！\n\n" +
                    "如有需要，请联系我们的客服了解详情。";
        } else if (userMessage.contains("你好") || userMessage.contains("您好") || userMessage.contains("嗨")) {
            response = "你好呀！很高兴为你服务！😊\n\n请问有什么可以帮到你的吗？";
        } else if (userMessage.contains("再见") || userMessage.contains("拜拜") || userMessage.contains("结束")) {
            response = "再见！祝你和宠物生活愉快！🐾";
        } else if (userMessage.contains("谢谢") || userMessage.contains("感谢")) {
            response = "不客气！能帮到你我很开心！😊";
        } else {
            response = "🐾 关于宠物的问题，我很乐意为你解答！\n\n" +
                    "我可以提供以下帮助：\n" +
                    "• 宠物健康建议\n" +
                    "• 产品推荐\n" +
                    "• 日常护理指南\n" +
                    "• 训练技巧\n\n" +
                    "请问你有什么具体问题吗？";
        }
        
        // 添加AI回复
        try {
            Message aiMessage = new Message(Message.TYPE_AI, response);
            messageList.add(aiMessage);
            messageAdapter.notifyDataSetChanged();
            rvMessages.scrollToPosition(messageList.size() - 1);
            Log.d(TAG, "simulateAIResponse: AI回复添加成功");
        } catch (Exception e) {
            Log.e(TAG, "simulateAIResponse: AI回复添加失败", e);
        }
    }
}
