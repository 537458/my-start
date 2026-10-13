package com.petshop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.petshop.common.Response;
import com.petshop.entity.User;
import com.petshop.exception.BusinessException;
import com.petshop.mapper.UserMapper;
import com.petshop.service.UserService;
import com.petshop.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public User registerUser(User user) {
        log.info("开始用户注册: {}", user.getUsername());

        // 唯一索引只宽容 NULL，空串之间仍会彼此碰撞，所以入库前统一归一化成 NULL
        user.setPhone(normalizeBlankToNull(user.getPhone()));
        user.setEmail(normalizeBlankToNull(user.getEmail()));

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", user.getUsername());
        if (userMapper.selectOne(wrapper) != null) {
            log.warn("用户名已存在: {}", user.getUsername());
            throw new BusinessException("用户名已存在");
        }

        // 只有当email不为null时才检查邮箱是否存在
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            wrapper.clear();
            wrapper.eq("email", user.getEmail());
            if (userMapper.selectOne(wrapper) != null) {
                log.warn("邮箱已存在: {}", user.getEmail());
                throw new BusinessException("邮箱已存在");
            }
        }

        // 只有当phone不为null时才检查电话是否存在
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            wrapper.clear();
            wrapper.eq("phone", user.getPhone());
            if (userMapper.selectOne(wrapper) != null) {
                log.warn("电话已存在: {}", user.getPhone());
                throw new BusinessException("该手机号已绑定其他账号");
            }
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);
        log.info("用户注册成功: {}", user.getUsername());
        return user;
    }

    @Override
    public User login(String username, String password) {
        log.info("用户登录尝试: {}", username);

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            log.warn("用户不存在: {}", username);
            throw new BusinessException("用户名或密码错误");
        }

        // 安全要求：明文密码与哈希一律不得写入日志
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("密码错误: {}", username);
            throw new BusinessException("用户名或密码错误");
        }

        log.info("用户登录成功: {}", username);
        return user;
    }

    @Override
    public User findById(Integer id) {
        return userMapper.selectById(id);
    }

    @Override
    public User update(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        return user;
    }

    @Override
    public void delete(Integer id) {
        userMapper.deleteById(id);
    }

    @Override
    public List<User> findAll() {
        return userMapper.selectList(null);
    }

    @Override
    public boolean updatePassword(Integer userId, String oldPassword, String newPassword) {
        log.info("开始更新用户密码: userId={}", userId);

        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("用户不存在: userId={}", userId);
            return false;
        }

        // 验证原密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            log.warn("原密码错误: userId={}", userId);
            return false;
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("用户密码更新成功: userId={}", userId);
        return true;
    }
    
    @Override
    public Response<Void> register(User user) {
        try {
            User registered = registerUser(user);
            return Response.success("注册成功", (Void) null);
        } catch (BusinessException e) {
            return Response.error(e.getMessage());
        }
    }
    
    @Override
    public Response<String> loginByPhone(String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            return Response.error("该手机号尚未注册");
        }
        
        Map<String, Object> claims = java.util.Map.of(
            "sub", user.getId(),
            "username", user.getUsername()
        );
        String token = jwtUtil.generateToken(claims);
        return Response.success(token);
    }
    
    @Override
    public Response<Void> updatePassword(Integer userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Response.error("用户不存在");
        }
        
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        
        return Response.success("密码修改成功", (Void) null);
    }
    
    @Override
    public User getUserByPhone(String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone)
                // phone 已上唯一索引，但旧库可能还没补齐约束，保留 LIMIT 1 防止 selectOne 撞多行报错
                .orderByAsc("id")
                .last("LIMIT 1");
        return userMapper.selectOne(wrapper);
    }

    @Override
    public long countUsersByPhone(String phone) {
        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq("phone", phone);
        Long count = userMapper.selectCount(wrapper);
        return count == null ? 0L : count;
    }

    @Override
    public boolean isTakenByOther(String column, String value, Integer excludeId) {
        // 列名直接拼进 SQL，必须先过白名单
        switch (column) {
            case "username":
            case "phone":
            case "email":
                break;
            default:
                throw new IllegalArgumentException("不支持的唯一字段: " + column);
        }

        String normalized = normalizeBlankToNull(value);
        if (normalized == null) {
            return false;
        }

        QueryWrapper<User> wrapper = new QueryWrapper<>();
        wrapper.eq(column, normalized).ne(excludeId != null, "id", excludeId);
        Long count = userMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private String normalizeBlankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    @Override
    public boolean resetAvatar(Integer userId) {
        if (userId == null) {
            return false;
        }
        boolean cleared = userMapper.clearAvatar(userId) > 0;
        log.info("用户头像已恢复默认: userId={}, cleared={}", userId, cleared);
        return cleared;
    }
}