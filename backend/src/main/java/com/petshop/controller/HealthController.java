package com.petshop.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @GetMapping
    public ResponseEntity<?> healthCheck() {
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("status", "UP");
        response.put("message", "PetShop API is running successfully");
        
        // 检查数据库连接。
        // 这个接口是免登录的（内网穿透后直接暴露在公网），
        // 所以只回一个 CONNECTED / ERROR 状态，不把 JDBC 串、库名、数据库版本泄出去
        try (Connection conn = dataSource.getConnection()) {
            response.put("database", "CONNECTED");
        } catch (Exception e) {
            response.put("database", "ERROR");
        }
        
        return ResponseEntity.ok(response);
    }
}