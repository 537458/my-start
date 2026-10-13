-- 商品图片表（用于存储图片二进制数据）
CREATE TABLE IF NOT EXISTS product_images (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    image_data LONGBLOB NOT NULL COMMENT '图片二进制数据',
    file_name VARCHAR(255) COMMENT '原始文件名',
    file_type VARCHAR(100) NOT NULL COMMENT 'MIME类型，如 image/jpeg',
    file_size BIGINT NOT NULL COMMENT '文件大小（字节）',
    is_main_image BOOLEAN DEFAULT FALSE COMMENT '是否主图',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_product (product_id),
    INDEX idx_product_main (product_id, is_main_image)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入测试商品图片数据示例（仅作为参考，实际使用时应通过应用逻辑插入）
-- INSERT INTO product_images (product_id, image_data, file_name, file_type, file_size, is_main_image)
-- VALUES (1, LOAD_FILE('/path/to/image.jpg'), 'image.jpg', 'image/jpeg', 12345, TRUE);
