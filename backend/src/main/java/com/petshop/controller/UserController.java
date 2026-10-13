package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.entity.User;
import com.petshop.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @GetMapping("/me")
    public Response<User> getCurrentUser() {
        Integer userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userService.findById(userId);
        
        // 设置头像URL
        if (user != null && user.getAvatarData() != null && user.getAvatarData().length > 0) {
            user.setAvatar("/api/users/me/avatar/data");
        } else {
            user.setAvatar("default_avatar");
        }
        
        return Response.success(user);
    }

    /**
     * 查看指定用户资料。
     * 用户表含有手机号、邮箱等个人信息，只允许本人或管理员查看，
     * 否则任意登录用户都能遍历全站用户资料。
     */
    @GetMapping("/{id}")
    public Response<User> getUserById(@PathVariable Integer id, Authentication authentication) {
        Integer currentUserId = resolveUserId(authentication);
        if (currentUserId == null) {
            return Response.error(401, "请先登录");
        }
        if (!currentUserId.equals(id) && !isAdmin(currentUserId)) {
            return Response.error(403, "无权查看该用户信息");
        }
        User user = userService.findById(id);
        if (user == null) {
            return Response.error(404, "用户不存在");
        }
        return Response.success(user);
    }

    /**
     * 解析当前登录用户ID
     */
    private Integer resolveUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(authentication.getPrincipal()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 管理员判定：查库确认 role=1
     */
    private boolean isAdmin(Integer userId) {
        if (userId == null) {
            return false;
        }
        User user = userService.findById(userId);
        return user != null && Integer.valueOf(1).equals(user.getRole());
    }

    /**
     * 更新当前用户资料。
     * 只接受资料类字段，role / password / balance 等敏感字段即使提交也会被丢弃，
     * 避免客户端通过本接口提权或覆盖密码。
     */
    @PutMapping("/me")
    public Response<User> updateCurrentUser(@RequestBody Map<String, Object> payload) {
        Integer userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        User existing = userService.findById(userId);
        if (existing == null) {
            return Response.error(404, "用户不存在");
        }

        // 字段白名单：未列出的字段一律不参与更新
        User patch = new User();
        patch.setId(userId);

        String username = readText(payload, "username");
        if (username != null && !username.isEmpty()) {
            if (userService.isTakenByOther("username", username, userId)) {
                return Response.error(400, "用户名已被其他账号使用");
            }
            patch.setUsername(username);
        }
        String phone = readText(payload, "phone");
        // 空值不参与更新：否则写入的空串会跟其他空串在同一唯一索引上碰撞
        if (phone != null && !phone.isEmpty()) {
            if (userService.isTakenByOther("phone", phone, userId)) {
                return Response.error(400, "该手机号已绑定其他账号");
            }
            patch.setPhone(phone);
        }
        String email = readText(payload, "email");
        if (email != null && !email.isEmpty()) {
            if (userService.isTakenByOther("email", email, userId)) {
                return Response.error(400, "邮箱已被其他账号使用");
            }
            patch.setEmail(email);
        }

        userService.update(patch);
        log.info("用户资料更新成功：{}", userId);

        // 重新读取：patch 对象不含 avatarData，直接用它判断头像会误判为默认头像
        User saved = userService.findById(userId);
        if (saved != null) {
            if (saved.getAvatarData() != null && saved.getAvatarData().length > 0) {
                saved.setAvatar("/api/users/me/avatar/data");
            } else {
                saved.setAvatar("default_avatar");
            }
        }

        return Response.success(saved);
    }

    /**
     * 读取白名单内的文本字段；字段未出现时返回 null，表示不参与本次更新。
     */
    private String readText(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) {
            return null;
        }
        Object value = payload.get(key);
        return value == null ? "" : value.toString().trim();
    }
        
    /**
     * 上传头像（存储到数据库）
     */
    @PostMapping("/me/avatar")
    public Response<Map<String, String>> uploadAvatar(@RequestParam("avatar") MultipartFile file) {
        String logPrefix = "[头像上传] ";
        
        try {
            if (file.isEmpty()) {
                log.warn("{}文件为空，上传失败", logPrefix);
                return Response.error(400, "文件不能为空");
            }
                
            // 获取当前用户 ID
            Integer userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            
            // 记录上传文件信息
            String originalFilename = file.getOriginalFilename();
            long fileSize = file.getSize();
            String contentType = file.getContentType();
            
            log.info("{}开始上传头像 - userId: {}, 文件名: {}, 文件大小: {}KB, MIME类型: {}", 
                     logPrefix, userId, originalFilename, fileSize / 1024, contentType);
            
            // 验证文件类型（image/* 这种通配值不能入库，写响应头时会被 Spring 拒掉）
            if (contentType == null || !contentType.startsWith("image/") || contentType.contains("*")) {
                log.warn("{}文件类型不正确 - contentType: {}", logPrefix, contentType);
                return Response.error(400, "请上传有效的图片文件");
            }
            
            // 读取文件二进制数据
            byte[] avatarData = file.getBytes();
            log.info("{}读取文件成功，二进制数据大小: {} bytes", logPrefix, avatarData.length);
            
            // 更新用户头像（存储到数据库）
            try {
                User user = userService.findById(userId);
                if (user != null) {
                    user.setAvatarData(avatarData);
                    user.setAvatarType(contentType);
                    User updatedUser = userService.update(user);
                    log.info("{}用户头像更新成功（数据库存储）- userId: {}, 头像大小: {}KB, MIME类型: {}", 
                        logPrefix, userId, avatarData.length / 1024, contentType);
                } else {
                    log.error("{}用户不存在 - userId: {}", logPrefix, userId);
                    return Response.error(404, "用户不存在");
                }
            } catch (Exception e) {
                log.error("{}更新用户头像失败: {}", logPrefix, e.getMessage(), e);
                return Response.error(500, "更新头像失败：" + e.getMessage());
            }
                
            // 返回成功信息
            Map<String, String> result = new HashMap<>();
            result.put("message", "头像上传成功");
            result.put("avatarUrl", "/api/users/me/avatar/data"); // 头像数据获取接口
                
            log.info("{}头像上传完成（数据库存储）- userId: {}", logPrefix, userId);
            return Response.success(result);
                
        } catch (Exception e) {
            log.error("{}上传头像异常: {}", logPrefix, e.getMessage(), e);
            return Response.error(500, "上传失败：" + e.getMessage());
        }
    }

    /**
     * 恢复默认头像：清空库里的头像二进制与 MIME 类型
     */
    @DeleteMapping("/me/avatar")
    public Response<Map<String, String>> resetAvatar() {
        Integer userId;
        try {
            userId = (Integer) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        } catch (Exception e) {
            log.warn("[恢复默认头像] 用户未认证");
            return Response.error(401, "请先登录");
        }
        User user = userService.findById(userId);
        if (user == null) {
            return Response.error(404, "用户不存在");
        }
        userService.resetAvatar(userId);
        log.info("[恢复默认头像] 已清空头像 - userId: {}", userId);

        Map<String, String> result = new HashMap<>();
        result.put("message", "已恢复默认头像");
        result.put("avatarUrl", "default_avatar");
        return Response.success("已恢复默认头像", result);
    }
    
    /**
     * 获取当前用户头像（二进制数据）
     */
    @GetMapping("/me/avatar/data")
    public ResponseEntity<byte[]> getCurrentUserAvatar() {
        String logPrefix = "[获取头像] ";
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        // 1. 先验证认证状态
        if (authentication == null || authentication.getPrincipal() == null || 
            "anonymousUser".equals(authentication.getPrincipal())) {
            log.warn("{}用户未认证，返回401", logPrefix);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // 2. 验证用户ID
        Integer userId;
        try {
            userId = (Integer) authentication.getPrincipal();
        } catch (ClassCastException e) {
            log.error("{}用户ID转换失败: {}", logPrefix, e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        log.info("{}开始获取头像 - userId: {}", logPrefix, userId);
        
        // 3. 查询用户
        User user;
        try {
            user = userService.findById(userId);
        } catch (Exception e) {
            log.error("{}查询用户失败: {}", logPrefix, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        
        if (user == null) {
            log.warn("{}用户不存在 - userId: {}", logPrefix, userId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        
        // 4. 返回头像
        if (user.getAvatarData() != null && user.getAvatarData().length > 0) {
            String contentType = resolveAvatarContentType(user.getAvatarType());
            log.info("{}返回二进制头像 - userId: {}, 大小: {}KB, MIME类型: {}", 
                logPrefix, userId, user.getAvatarData().length / 1024, contentType);
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // 头像是“当前登录人”的数据，换账号后同一个地址内容会变，禁止任何缓存
                .cacheControl(CacheControl.noStore())
                .body(user.getAvatarData());
        }
        
        log.info("{}用户没有头像 - userId: {}", logPrefix, userId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 归一化头像 MIME：早期数据里可存在 image/* 这类通配值，
     * 直接交给 ResponseEntity.contentType 会抛 IllegalArgumentException 导致接口 500
     */
    private String resolveAvatarContentType(String stored) {
        if (stored == null || stored.isEmpty() || stored.contains("*")) {
            return "image/jpeg";
        }
        return stored;
    }
    
    /**
     * 根据用户ID获取头像（二进制数据）
     */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<byte[]> getUserAvatar(@PathVariable Integer id) {
        String logPrefix = "[获取用户头像] ";
        try {
            User user = userService.findById(id);
            
            if (user == null) {
                log.warn("{}用户不存在 - userId: {}", logPrefix, id);
                return ResponseEntity.notFound().build();
            }
            
            // 使用二进制头像
            if (user.getAvatarData() != null && user.getAvatarData().length > 0) {
                String contentType = resolveAvatarContentType(user.getAvatarType());
                log.info("{}返回二进制头像 - userId: {}, 大小: {}KB, MIME类型: {}", 
                    logPrefix, id, user.getAvatarData().length / 1024, contentType);
                
                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    // 对方改头像后同一个地址内容会变，禁止任何缓存，避免客户端命中旧图
                    .cacheControl(CacheControl.noStore())
                    .body(user.getAvatarData());
            }
            
            // 没有头像
            log.info("{}用户没有头像 - userId: {}", logPrefix, id);
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("{}获取头像异常: {}", logPrefix, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 用户列表仅管理员可用，避免全站用户的手机号/邮箱被任意账号拉取
     */
    @GetMapping
    public Response<List<User>> getAllUsers(Authentication authentication) {
        Integer currentUserId = resolveUserId(authentication);
        if (currentUserId == null) {
            return Response.error(401, "请先登录");
        }
        if (!isAdmin(currentUserId)) {
            return Response.error(403, "需要管理员权限");
        }
        List<User> users = userService.findAll();
        return Response.success(users);
    }

    @PutMapping("/me/password")
    public Response<Void> updatePassword(@RequestBody Map<String, String> params) {
        try {
            // 检查SecurityContext是否有认证信息
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("未认证的请求：{}", params);
                return Response.error(401, "未登录");
            }
            
            Integer userId = (Integer) authentication.getPrincipal();
            String oldPassword = params.get("oldPassword");
            String newPassword = params.get("newPassword");

            log.info("接收到密码修改请求：userId={}", userId);

            boolean success = userService.updatePassword(userId, oldPassword, newPassword);
            if (success) {
                log.info("用户密码更新成功：{}", userId);
                return Response.success();
            } else {
                log.warn("用户密码更新失败：{}", userId);
                return Response.error(400, "原密码错误");
            }
        } catch (Exception e) {
            log.error("密码修改异常：", e);
            return Response.error(500, "服务器内部错误");
        }
    }

    @GetMapping("/test")
    public Response<String> test() {
        return Response.success("Hello from backend!");
    }
}