package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.entity.User;
import com.petshop.service.SmsService;
import com.petshop.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthControllerV2 {
    
    @Autowired
    private SmsService smsService;
    
    @Autowired
    private UserService userService;
    
    /**
     * 发送短信验证码
     */
    @PostMapping("/send-sms")
    public Response<Void> sendSms(@RequestParam String phone, @RequestParam String type) {
        return smsService.sendSmsCode(phone, type);
    }
    
    /**
     * 验证码注册
     */
    @PostMapping("/register-with-code")
    public Response<Void> registerWithCode(@RequestBody User user, @RequestParam String code) {
        // 先验证验证码
        Response<Boolean> verifyResult = smsService.verifySmsCode(user.getPhone(), code, "register");
        if (!verifyResult.isSuccess()) {
            return Response.error(verifyResult.getMessage());
        }
        
        return userService.register(user);
    }
    
    /**
     * 手机号验证码登录
     */
    @PostMapping("/login-with-code")
    public Response<String> loginWithCode(@RequestParam String phone, @RequestParam String code) {
        // 先验证验证码
        Response<Boolean> verifyResult = smsService.verifySmsCode(phone, code, "login");
        if (!verifyResult.isSuccess()) {
            return Response.error(verifyResult.getMessage());
        }
        
        return userService.loginByPhone(phone);
    }
    
    /**
     * 找回密码 - 发送验证码
     * 先确认手机号已注册，避免给陌生号码发一个永远用不上的验证码
     */
    @PostMapping("/reset-password/send-code")
    public Response<Void> sendResetCode(@RequestParam String phone) {
        long accounts = userService.countUsersByPhone(phone);
        if (accounts == 0) {
            return Response.error("该手机号尚未注册");
        }
        if (accounts > 1) {
            // 手机号不唯一时无法判断要重置哪个账号，强行做会改到别人的密码
            return Response.error("该手机号绑定了多个账号，请在登录后使用「修改密码」");
        }
        return smsService.sendSmsCode(phone, "reset_password");
    }
    
    /**
     * 找回密码 - 验证并重置密码
     */
    @PostMapping("/reset-password")
    public Response<Void> resetPassword(@RequestParam String phone, 
                                         @RequestParam String code, 
                                         @RequestParam String newPassword) {
        // 这是免登录的公开接口，长度校验必须放在服务端，不能只靠客户端
        if (newPassword == null || newPassword.trim().length() < 6) {
            return Response.error("密码长度不能少于6位");
        }

        // 验证验证码
        Response<Boolean> verifyResult = smsService.verifySmsCode(phone, code, "reset_password");
        if (!verifyResult.isSuccess()) {
            return Response.error(verifyResult.getMessage());
        }
        
        // 更新密码
        User user = userService.getUserByPhone(phone);
        if (user == null) {
            return Response.error("用户不存在");
        }
        if (userService.countUsersByPhone(phone) > 1) {
            return Response.error("该手机号绑定了多个账号，请在登录后使用「修改密码」");
        }
        
        return userService.updatePassword(user.getId(), newPassword.trim());
    }
}
