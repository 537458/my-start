package com.petshop.controller;

import com.petshop.common.Response;
import com.petshop.config.DbInitializer;
import com.petshop.mapper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/init")
public class InitController {

    private static final Logger log = LoggerFactory.getLogger(InitController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DbInitializer dbInitializer;

    @GetMapping("/db")
    public Response<String> initDatabase() {
        try {
            log.info("开始初始化数据库...");
            dbInitializer.executeInitSql();
            return Response.success("数据库初始化成功！", null);
        } catch (Exception e) {
            log.error("数据库初始化失败", e);
            return Response.error(500, "数据库初始化失败: " + e.getMessage());
        }
    }

    @PostMapping("/reset")
    public Response<String> resetDatabase() {
        try {
            log.info("开始重置数据库...");
            
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("TRUNCATE TABLE order_items");
            jdbcTemplate.execute("TRUNCATE TABLE orders");
            jdbcTemplate.execute("TRUNCATE TABLE shopping_carts");
            jdbcTemplate.execute("TRUNCATE TABLE user_pets");
            jdbcTemplate.execute("TRUNCATE TABLE products");
            jdbcTemplate.execute("TRUNCATE TABLE categories");
            jdbcTemplate.execute("TRUNCATE TABLE users");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
            
            dbInitializer.executeInitSql();
            
            return Response.success("数据库重置成功！", null);
        } catch (Exception e) {
            log.error("数据库重置失败", e);
            return Response.error(500, "数据库重置失败: " + e.getMessage());
        }
    }
}
