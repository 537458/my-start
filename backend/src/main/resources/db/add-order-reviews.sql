-- 商品评价表（若数据库由旧版 database.sql 创建，需执行本脚本补齐）
CREATE TABLE IF NOT EXISTS order_reviews (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    rating INT NOT NULL COMMENT '评分，1-5星',
    content TEXT,
    images VARCHAR(500) COMMENT '评价图片，多个图片用逗号分隔',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_user (user_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单评价表';
