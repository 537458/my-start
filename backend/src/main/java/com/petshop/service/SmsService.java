package com.petshop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petshop.common.Response;
import com.petshop.entity.SmsCode;
import com.petshop.mapper.SmsCodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务。
 * 当前为模拟实现（未接入真实短信网关），因此：
 * - 验证码用 SecureRandom 生成，避免可预测的伪随机序列；
 * - 验证码永远不出现在接口响应里，防止任意人凭响应直接接管账号；
 * - 本地联调可开启演示模式，验证码仅写入本地运行日志；
 * - 同一手机号同类型 60 秒内不可重复发送，单个验证码最多允许 5 次错误尝试。
 */
@Service
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    /** 验证码有效期（分钟） */
    private static final int CODE_EXPIRE_MINUTES = 5;

    /** 重发间隔（秒） */
    private static final int RESEND_INTERVAL_SECONDS = 60;

    /** 单个验证码允许的最大错误尝试次数 */
    private static final int MAX_FAIL_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 错误尝试计数：key = phone + ":" + type */
    private final Map<String, Integer> failAttempts = new ConcurrentHashMap<>();

    /**
     * 演示模式：未接入短信网关时，仅把验证码写进本地运行日志方便联调。
     * 无论开关如何，验证码都不会出现在接口响应里。
     */
    @Value("${petshop.sms.demo-mode:false}")
    private boolean demoMode;

    @Autowired
    private SmsCodeMapper smsCodeMapper;

    /**
     * 发送短信验证码（模拟实现，实际项目中接入阿里云/腾讯云短信服务）
     */
    public Response<Void> sendSmsCode(String phone, String type) {
        if (phone == null || phone.trim().isEmpty()) {
            return Response.error("手机号不能为空");
        }
        phone = phone.trim();
        if (!phone.matches("^1[3-9]\\d{9}$")) {
            return Response.error("手机号格式不正确");
        }
        if (type == null || type.trim().isEmpty()) {
            type = "register";
        }

        // 频率限制：同一手机号同类型 60 秒内只能发一次，防止被当成短信轰炸机
        LambdaQueryWrapper<SmsCode> recentWrapper = new LambdaQueryWrapper<>();
        recentWrapper.eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getType, type)
                .gt(SmsCode::getCreatedAt, LocalDateTime.now().minusSeconds(RESEND_INTERVAL_SECONDS));
        Long recent = smsCodeMapper.selectCount(recentWrapper);
        if (recent != null && recent > 0) {
            return Response.error("验证码发送过于频繁，请 " + RESEND_INTERVAL_SECONDS + " 秒后再试");
        }

        String code = generateCode();

        // 删除该手机号之前未使用的验证码
        LambdaQueryWrapper<SmsCode> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getType, type)
                .eq(SmsCode::getIsUsed, false);
        smsCodeMapper.delete(deleteWrapper);

        // 保存新验证码
        SmsCode smsCode = new SmsCode();
        smsCode.setPhone(phone);
        smsCode.setCode(code);
        smsCode.setType(type);
        smsCode.setIsUsed(false);
        smsCode.setExpiresAt(LocalDateTime.now().plusMinutes(CODE_EXPIRE_MINUTES));
        smsCode.setCreatedAt(LocalDateTime.now());
        smsCodeMapper.insert(smsCode);

        failAttempts.remove(failKey(phone, type));

        // 真实短信网关在此处发送；日志只留脱敏手机号，绝不写验证码
        log.info("【短信验证码】手机号：{}，类型：{}，已发送", maskPhone(phone), type);

        if (demoMode) {
            // 本地联调：验证码只落到本地运行日志，绝不回显给调用方
            log.warn("【演示模式】手机号：{}，类型：{}，验证码：{}（仅限本地联调，不会返回给客户端）",
                    maskPhone(phone), type, code);
        }
        return Response.success("验证码已发送", (Void) null);
    }

    /**
     * 验证短信验证码
     */
    public Response<Boolean> verifySmsCode(String phone, String code, String type) {
        if (phone == null || code == null || type == null) {
            return Response.error("验证码错误或已过期");
        }
        String key = failKey(phone, type);

        Integer attempts = failAttempts.get(key);
        if (attempts != null && attempts >= MAX_FAIL_ATTEMPTS) {
            // 已经试错太多次，当前验证码作废，必须重新获取
            deleteUnusedCodes(phone, type);
            failAttempts.remove(key);
            return Response.error("验证码错误次数过多，请重新获取");
        }

        LambdaQueryWrapper<SmsCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getCode, code)
                .eq(SmsCode::getType, type)
                .eq(SmsCode::getIsUsed, false)
                .gt(SmsCode::getExpiresAt, LocalDateTime.now())
                .orderByDesc(SmsCode::getCreatedAt)
                .last("LIMIT 1");

        SmsCode smsCode = smsCodeMapper.selectOne(wrapper);

        if (smsCode == null) {
            int used = failAttempts.merge(key, 1, Integer::sum);
            log.warn("验证码校验失败：phone={}, type={}, 已失败 {} 次", maskPhone(phone), type, used);
            if (used >= MAX_FAIL_ATTEMPTS) {
                deleteUnusedCodes(phone, type);
                failAttempts.remove(key);
                return Response.error("验证码错误次数过多，请重新获取");
            }
            return Response.error("验证码错误或已过期");
        }

        // 标记为已使用
        smsCode.setIsUsed(true);
        smsCodeMapper.updateById(smsCode);
        failAttempts.remove(key);

        return Response.success(true);
    }

    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }

    private void deleteUnusedCodes(String phone, String type) {
        LambdaQueryWrapper<SmsCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SmsCode::getPhone, phone)
                .eq(SmsCode::getType, type)
                .eq(SmsCode::getIsUsed, false);
        smsCodeMapper.delete(wrapper);
    }

    private String failKey(String phone, String type) {
        return phone + ":" + type;
    }

    /** 日志脱敏：138****8000 */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
