package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.entity.User;
import com.petshop.service.UserService;
import com.petshop.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public Response<User> register(@RequestBody User user) {
        User registeredUser = userService.registerUser(user);
        log.info("用户注册成功: {}", user.getUsername());
        return Response.success("注册成功", registeredUser);
    }

    @PostMapping("/login")
    public Response<Map<String, Object>> login(@RequestBody Map<String, String> loginData) {
        String username = loginData.get("username");
        String password = loginData.get("password");
        User user = userService.login(username, password);

        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", String.valueOf(user.getId()));
        claims.put("username", user.getUsername());
        claims.put("role", String.valueOf(user.getRole()));
        String token = jwtUtil.generateToken(claims);

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("user", user);

        log.info("用户登录成功: {}", username);
        return Response.success("登录成功", response);
    }
}