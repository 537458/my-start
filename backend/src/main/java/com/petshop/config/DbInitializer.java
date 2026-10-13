package com.petshop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
public class DbInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DbInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean isDatabaseEmpty() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            return count == null || count == 0;
        } catch (Exception e) {
            log.info("users 表不存在，需要初始化数据库");
            return true;
        }
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("检查数据库是否需要初始化...");
        
        try {
            jdbcTemplate.execute("CREATE DATABASE IF NOT EXISTS petshop CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            log.info("✅ 数据库 petshop 创建成功或已存在");
        } catch (Exception e) {
            log.warn("创建数据库时出现警告: {}", e.getMessage());
        }
        
        try {
            jdbcTemplate.execute("USE petshop");
            log.info("✅ 切换到 petshop 数据库");
        } catch (Exception e) {
            log.error("无法切换到 petshop 数据库: {}", e.getMessage());
            return;
        }
        
        boolean needInit = isDatabaseEmpty();
        
        if (needInit) {
            log.info("开始从 init.sql 初始化数据库...");
            executeInitSql();
        } else {
            log.info("✅ 数据库已存在，检查并添加必要的列");
            checkAndAddExpiresAtColumn();
            checkAndModifyEmailColumn();
            ensureUniquePhone();
            checkAndAddAddressColumn();
            checkAndAddAvatarColumns();
            dropRedundantColumns();
        }

        // 收藏 / 优惠券 / 消息通知是后增功能，旧库需要幂等补齐
        ensureFeatureSchema();
        // 社区是后增功能，旧库可能整张表都没有；可见范围列也是后加的
        ensureCommunitySchema();
        // 我的宠物也是后增功能，旧库没有这张表
        ensurePetSchema();
    }

    /**
     * 幂等补齐「我的宠物」表。
     * 建表语句与 db/init.sql 保持一致，已有表不会被改动。
     */
    private void ensurePetSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS pets ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, name VARCHAR(20) NOT NULL, "
                + "species VARCHAR(20) DEFAULT 'cat' COMMENT 'cat 猫 / dog 狗 / other 其他', "
                + "breed VARCHAR(30) COMMENT '具体品种，如狸花猫、比格犬', "
                + "gender VARCHAR(10) DEFAULT 'unknown' COMMENT 'male 公 / female 母 / unknown 未知', "
                + "birthday DATE NULL, weight DECIMAL(5,2) NULL COMMENT '体重，单位kg', "
                + "note VARCHAR(100), avatar_data LONGBLOB, avatar_type VARCHAR(50), "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "INDEX idx_user (user_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        log.info("✅ 我的宠物表结构已就绪");
    }

    /**
     * 幂等补齐社区四张表与动态的可见范围列。
     * 建表语句与 db/init.sql 保持一致，已有表不会被改动。
     */
    private void ensureCommunitySchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_posts ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, content VARCHAR(1000) NOT NULL, "
                + "image_count TINYINT DEFAULT 0, like_count INT DEFAULT 0, comment_count INT DEFAULT 0, "
                + "status TINYINT DEFAULT 1, visibility TINYINT DEFAULT 0 COMMENT '0 公开 / 1 仅自己可见', "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "INDEX idx_created (created_at), INDEX idx_user (user_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_post_images ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, post_id INT NOT NULL, sort_order TINYINT DEFAULT 0, "
                + "content_type VARCHAR(50) DEFAULT 'image/jpeg', image_data LONGBLOB, "
                + "INDEX idx_post (post_id, sort_order)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_post_likes ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, post_id INT NOT NULL, user_id INT NOT NULL, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uk_post_user (post_id, user_id), INDEX idx_user (user_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS community_post_comments ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, post_id INT NOT NULL, user_id INT NOT NULL, "
                + "content VARCHAR(500) NOT NULL, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_post (post_id, id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        addColumnIfAbsent("community_posts", "visibility", "TINYINT DEFAULT 0 COMMENT '0 公开 / 1 仅自己可见'");
        log.info("✅ 社区相关表结构已就绪");
    }

    /**
     * 幂等补齐收藏、优惠券、消息通知相关的表与订单抵扣字段。
     * 新库由 init.sql 建表后此方法无副作用，旧库在此自动迁移。
     */
    private void ensureFeatureSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS product_favorites ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, product_id INT NOT NULL, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE KEY uk_user_product (user_id, product_id), INDEX idx_product (product_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS coupons ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, type VARCHAR(20) NOT NULL, "
                + "amount DECIMAL(10,2) DEFAULT 0.00, discount DECIMAL(3,2) DEFAULT 1.00, "
                + "min_amount DECIMAL(10,2) DEFAULT 0.00, total INT DEFAULT 0, received INT DEFAULT 0, "
                + "start_time DATETIME, end_time DATETIME, status TINYINT DEFAULT 1, "
                + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_coupons ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, coupon_id INT NOT NULL, "
                + "status VARCHAR(20) DEFAULT 'UNUSED', order_id INT, "
                + "received_at DATETIME DEFAULT CURRENT_TIMESTAMP, used_at DATETIME, "
                + "UNIQUE KEY uk_user_coupon (user_id, coupon_id), INDEX idx_user (user_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS notifications ("
                + "id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, type VARCHAR(30) NOT NULL, "
                + "title VARCHAR(100) NOT NULL, content VARCHAR(500), related_id INT, "
                + "is_read TINYINT DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_user_read (user_id, is_read)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

        addColumnIfAbsent("orders", "discount_amount", "DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠券抵扣金额'");
        addColumnIfAbsent("orders", "user_coupon_id", "INT COMMENT '本次订单使用的用户优惠券ID'");
        addColumnIfAbsent("orders", "pay_method", "VARCHAR(20) COMMENT '支付方式编码: WECHAT/ALIPAY/BANK/COD'");
        addColumnIfAbsent("orders", "paid_at", "DATETIME COMMENT '支付完成时间'");
        // Order 实体映射了这三个字段，旧库缺列时所有订单查询都会直接报 SQL 错误
        addColumnIfAbsent("orders", "merchant_id", "INT COMMENT '所属商家ID，NULL 表示自营'");
        addColumnIfAbsent("orders", "user_name", "VARCHAR(100) COMMENT '下单用户名快照'");
        addColumnIfAbsent("orders", "product_name", "VARCHAR(200) COMMENT '商品名快照'");
        addIndexIfAbsent("orders", "idx_merchant", "merchant_id");

        seedCoupons();
        log.info("✅ 收藏 / 优惠券 / 消息通知 相关表结构已就绪");
    }

    private void addColumnIfAbsent(String table, String column, String definition) {
        try {
            jdbcTemplate.queryForObject("SELECT " + column + " FROM " + table + " LIMIT 1", Object.class);
        } catch (Exception e) {
            try {
                jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                log.info("✅ {}.{} 列添加成功", table, column);
            } catch (Exception ex) {
                log.warn("添加列 {}.{} 时出现警告: {}", table, column, ex.getMessage());
            }
        }
    }

    private void addIndexIfAbsent(String table, String indexName, String column) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.STATISTICS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                    Integer.class, table, indexName);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD INDEX " + indexName + " (" + column + ")");
            log.info("✅ {}.{} 索引添加成功", table, indexName);
        } catch (Exception e) {
            log.warn("添加索引 {}.{} 失败: {}", table, indexName, e.getMessage());
        }
    }

    /** 优惠券模板只在表为空时初始化一次，避免重复发券 */
    private void seedCoupons() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM coupons", Integer.class);
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.update("INSERT INTO coupons (name, type, amount, discount, min_amount, total, received, start_time, end_time, status) VALUES "
                    + "('新人立减券', 'FULL_REDUCE', 10.00, 1.00, 100.00, 1000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR), 1),"
                    + "('满200减30', 'FULL_REDUCE', 30.00, 1.00, 200.00, 500, 0, NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR), 1),"
                    + "('全场9折券', 'DISCOUNT', 0.00, 0.90, 50.00, 300, 0, NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR), 1),"
                    + "('满50减5', 'FULL_REDUCE', 5.00, 1.00, 50.00, 2000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR), 1)");
            log.info("✅ 优惠券模板初始化完成");
        } catch (Exception e) {
            log.warn("初始化优惠券模板失败: {}", e.getMessage());
        }
    }
    
    private void checkAndAddAvatarColumns() {
        try {
            jdbcTemplate.queryForObject("SELECT avatar_data FROM users LIMIT 1", Object.class);
            log.info("✅ avatar_data 列已存在");
        } catch (Exception e) {
            log.info("正在添加 avatar_data 列到 users 表...");
            try {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN avatar_data LONGBLOB COMMENT '头像二进制数据'");
                log.info("✅ avatar_data 列添加成功！");
            } catch (Exception ex) {
                log.warn("添加 avatar_data 列时出现警告: {}", ex.getMessage());
            }
        }
        
        try {
            jdbcTemplate.queryForObject("SELECT avatar_type FROM users LIMIT 1", Object.class);
            log.info("✅ avatar_type 列已存在");
        } catch (Exception e) {
            log.info("正在添加 avatar_type 列到 users 表...");
            try {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN avatar_type VARCHAR(50) COMMENT '头像MIME类型'");
                log.info("✅ avatar_type 列添加成功！");
            } catch (Exception ex) {
                log.warn("添加 avatar_type 列时出现警告: {}", ex.getMessage());
            }
        }
    }
    
    private void checkAndAddExpiresAtColumn() {
        try {
            jdbcTemplate.queryForObject("SELECT expires_at FROM orders LIMIT 1", Object.class);
            log.info("✅ expires_at 列已存在");
        } catch (Exception e) {
            log.info("正在添加 expires_at 列到 orders 表...");
            try {
                jdbcTemplate.execute("ALTER TABLE orders ADD COLUMN expires_at DATETIME");
                log.info("✅ expires_at 列添加成功！");
            } catch (Exception ex) {
                log.warn("添加 expires_at 列时出现警告: {}", ex.getMessage());
            }
        }
    }

    private void checkAndModifyEmailColumn() {
        try {
            // 不能再写成 "MODIFY email VARCHAR(100) UNIQUE"：MySQL 会把 UNIQUE 当作再建一个唯一索引，
            // 于是每次启动多出一个 email_2 / email_3 ... 的重复索引
            if (isColumnNotNull("users", "email")) {
                jdbcTemplate.execute("ALTER TABLE users MODIFY email VARCHAR(100) NULL");
                log.info("✅ users.email 已改为允许为 NULL");
            }
            if (!hasUniqueIndexOnColumn("users", "email")) {
                jdbcTemplate.execute("ALTER TABLE users ADD UNIQUE INDEX uk_email (email)");
                log.info("✅ users.email 唯一索引已创建");
            }
        } catch (Exception e) {
            log.warn("修改 email 字段时出现警告：{}", e.getMessage());
        }
    }

    /**
     * 手机号全局唯一。找回密码等场景要按手机号反查账号，
     * 一旦重号就无法判断目标账号，这类请求只能报错中断。
     */
    private void ensureUniquePhone() {
        try {
            if (hasUniqueIndexOnColumn("users", "phone")) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE users ADD UNIQUE INDEX uk_phone (phone)");
            log.info("✅ users.phone 唯一索引已创建");
        } catch (Exception e) {
            // 存量数据重号时建索引会失败，这里只告警而不是让应用起不来
            log.warn("创建 users.phone 唯一索引失败（可能存在重复手机号，需先清理存量数据）：{}", e.getMessage());
        }
    }

    private boolean isColumnNotNull(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND IS_NULLABLE = 'NO'",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    /** 按列判断是否已有唯一索引，不关心里层索引叫什么名字，避免名字不一致时重复建同一个索引 */
    private boolean hasUniqueIndexOnColumn(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT INDEX_NAME) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ? AND NON_UNIQUE = 0",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void checkAndAddAddressColumn() {
        try {
            jdbcTemplate.queryForObject("SELECT address FROM users LIMIT 1", Object.class);
            log.info("✅ address 列已存在");
        } catch (Exception e) {
            log.info("正在添加 address 列到 users 表...");
            try {
                jdbcTemplate.execute("ALTER TABLE users ADD COLUMN address VARCHAR(255)");
                log.info("✅ address 列添加成功！");
            } catch (Exception ex) {
                log.warn("添加 address 列时出现警告: {}", ex.getMessage());
            }
        }
    }

    public void executeInitSql() {
        try {
            ClassPathResource resource = new ClassPathResource("db/init.sql");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );
            
            StringBuilder sql = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                
                sql.append(line).append("\n");
                
                if (line.endsWith(";")) {
                    String statement = sql.toString().trim();
                    try {
                        log.debug("执行SQL: {}", statement.substring(0, Math.min(50, statement.length())));
                        jdbcTemplate.execute(statement);
                    } catch (Exception e) {
                        log.warn("执行SQL时出现警告: {}", e.getMessage());
                    }
                    sql = new StringBuilder();
                }
            }
            
            reader.close();
            log.info("✅ 数据库初始化完成！");
            log.info("测试账号: user1 / 123456");
            log.info("管理员账号: admin / admin123");
        } catch (Exception e) {
            log.error("❌ 数据库初始化失败", e);
        }
    }
    
    private void dropRedundantColumns() {
        String[] columns = {"img_url", "status", "img_data"};
        for (String column : columns) {
            try {
                jdbcTemplate.execute("ALTER TABLE products DROP COLUMN " + column);
                log.info("✅ 成功删除冗余字段: {}", column);
            } catch (Exception e) {
                log.warn("删除字段 {} 时出现警告（可能已不存在）: {}", column, e.getMessage());
            }
        }
    }
}
