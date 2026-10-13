-- 扩展数据库以支持商家功能
-- 执行时间：2026-04-15

USE petshop;

-- 1. 扩展 users 表，添加商家相关字段
ALTER TABLE users 
ADD COLUMN merchant_status INT DEFAULT 0 COMMENT '0-非商家, 1-待审核, 2-已通过, 3-已拒绝',
ADD COLUMN shop_name VARCHAR(100) COMMENT '店铺名称',
ADD COLUMN shop_description TEXT COMMENT '店铺简介',
ADD COLUMN shop_logo VARCHAR(255) COMMENT '店铺Logo',
ADD COLUMN shop_phone VARCHAR(20) COMMENT '店铺联系电话',
ADD COLUMN shop_address VARCHAR(255) COMMENT '店铺地址',
ADD COLUMN business_license VARCHAR(255) COMMENT '营业执照照片URL',
ADD COLUMN business_hours VARCHAR(100) COMMENT '营业时间',
ADD COLUMN shop_status INT DEFAULT 0 COMMENT '0-关闭, 1-营业中';

-- 2. 创建商家认证申请表
CREATE TABLE IF NOT EXISTS merchant_certifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL COMMENT '申请用户ID',
    shop_name VARCHAR(100) NOT NULL COMMENT '店铺名称',
    shop_description TEXT COMMENT '店铺简介',
    business_license VARCHAR(255) NOT NULL COMMENT '营业执照照片URL',
    contact_person VARCHAR(50) NOT NULL COMMENT '联系人',
    contact_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
    contact_email VARCHAR(100) COMMENT '联系邮箱',
    business_scope VARCHAR(200) COMMENT '经营范围',
    status INT DEFAULT 0 COMMENT '0-待审核, 1-已通过, 2-已拒绝',
    reject_reason TEXT COMMENT '拒绝原因',
    reviewer_id INT COMMENT '审核人ID',
    reviewed_at DATETIME COMMENT '审核时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家认证申请表';

-- 3. 扩展 products 表，添加商家关联字段
ALTER TABLE products
ADD COLUMN merchant_id INT COMMENT '商家用户ID',
ADD COLUMN sales INT DEFAULT 0 COMMENT '销量',
ADD COLUMN brand VARCHAR(100) COMMENT '品牌',
ADD COLUMN specifications VARCHAR(200) COMMENT '规格',
ADD COLUMN ingredients TEXT COMMENT '成分',
ADD COLUMN applicable_pets VARCHAR(200) COMMENT '适用宠物',
ADD COLUMN usage_instructions TEXT COMMENT '使用说明',
ADD COLUMN product_status INT DEFAULT 1 COMMENT '0-下架, 1-上架',
ADD INDEX idx_merchant (merchant_id);

-- 4. 创建店铺表
CREATE TABLE IF NOT EXISTS shops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    merchant_id INT NOT NULL COMMENT '商家用户ID',
    shop_name VARCHAR(100) NOT NULL COMMENT '店铺名称',
    shop_logo VARCHAR(255) COMMENT '店铺Logo',
    shop_description TEXT COMMENT '店铺简介',
    shop_phone VARCHAR(20) COMMENT '店铺电话',
    shop_address VARCHAR(255) COMMENT '店铺地址',
    business_hours VARCHAR(100) COMMENT '营业时间',
    shop_status INT DEFAULT 1 COMMENT '0-关闭, 1-营业中',
    total_sales INT DEFAULT 0 COMMENT '总销量',
    total_products INT DEFAULT 0 COMMENT '商品总数',
    follower_count INT DEFAULT 0 COMMENT '关注数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_merchant (merchant_id),
    INDEX idx_status (shop_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺表';

-- 5. 创建店铺关注表
CREATE TABLE IF NOT EXISTS shop_follows (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL COMMENT '关注用户ID',
    shop_id INT NOT NULL COMMENT '店铺ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_shop (user_id, shop_id),
    INDEX idx_user (user_id),
    INDEX idx_shop (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='店铺关注表';

-- 6. 创建需求发布表
CREATE TABLE IF NOT EXISTS demands (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL COMMENT '发布者ID',
    demand_type VARCHAR(50) NOT NULL COMMENT '需求类型：product-用品求购, service-服务需求',
    title VARCHAR(100) NOT NULL COMMENT '需求标题',
    description TEXT NOT NULL COMMENT '需求描述',
    expected_price DECIMAL(10,2) COMMENT '期望价格',
    deadline DATETIME COMMENT '时间要求',
    status INT DEFAULT 0 COMMENT '0-进行中, 1-已响应, 2-已完成, 3-已关闭',
    response_count INT DEFAULT 0 COMMENT '响应数',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_type (demand_type),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求发布表';

-- 7. 创建需求响应表
CREATE TABLE IF NOT EXISTS demand_responses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    demand_id INT NOT NULL COMMENT '需求ID',
    merchant_id INT NOT NULL COMMENT '响应的商家ID',
    response_type VARCHAR(50) NOT NULL COMMENT '响应类型：product-提供商品, service-提供服务',
    product_id INT COMMENT '关联商品ID',
    description TEXT COMMENT '响应描述',
    price DECIMAL(10,2) COMMENT '报价',
    status INT DEFAULT 0 COMMENT '0-待查看, 1-已接受, 2-已拒绝',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_demand (demand_id),
    INDEX idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求响应表';

-- 8. 扩展 appointments 表，添加商家关联
ALTER TABLE appointments
ADD COLUMN merchant_id INT COMMENT '服务提供商ID',
ADD COLUMN pet_id INT COMMENT '宠物ID',
ADD COLUMN service_price DECIMAL(10,2) COMMENT '服务价格',
ADD INDEX idx_merchant (merchant_id);

-- 9. 扩展 orders 表，添加商家关联
ALTER TABLE orders
ADD COLUMN merchant_id INT COMMENT '商家ID',
ADD INDEX idx_merchant (merchant_id);

-- 10. 创建短信验证码表
CREATE TABLE IF NOT EXISTS sms_codes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL COMMENT '手机号',
    code VARCHAR(10) NOT NULL COMMENT '验证码',
    type VARCHAR(50) NOT NULL COMMENT '验证码类型：register-注册, login-登录, reset_password-重置密码',
    is_used BOOLEAN DEFAULT false COMMENT '是否已使用',
    expires_at DATETIME NOT NULL COMMENT '过期时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_phone (phone),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信验证码表';

-- 11. 插入测试商家数据
INSERT INTO users (username, password, email, phone, role, merchant_status, shop_name) VALUES
('merchant1', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'merchant1@petshop.com', '13800138002', 0, 2, '萌宠乐园'),
('merchant2', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'merchant2@petshop.com', '13800138003', 0, 2, '宠物之家');

-- 12. 插入测试店铺数据
INSERT INTO shops (merchant_id, shop_name, shop_description, shop_phone, shop_address, business_hours, shop_status, total_sales, total_products) VALUES
(3, '萌宠乐园', '专业宠物用品店，品质保证', '13800138002', '北京市朝阳区宠物街88号', '9:00-21:00', 1, 1500, 50),
(4, '宠物之家', '您身边的宠物服务专家', '13800138003', '上海市浦东新区宠物路168号', '10:00-20:00', 1, 800, 30);

-- 13. 更新现有商品，关联商家
UPDATE products SET merchant_id = 3 WHERE category_id IN (1, 2, 3);
UPDATE products SET merchant_id = 4 WHERE category_id IN (4, 5, 6);
UPDATE products SET sales = FLOOR(RAND() * 500) WHERE merchant_id IS NOT NULL;

-- 14. 更新订单，关联商家
UPDATE orders o
INNER JOIN order_items oi ON o.id = oi.order_id
INNER JOIN products p ON oi.product_id = p.id
SET o.merchant_id = p.merchant_id
WHERE o.merchant_id IS NULL AND p.merchant_id IS NOT NULL;

-- 15. 更新预约，添加服务提供商
UPDATE appointments SET merchant_id = 3 WHERE service_name IN ('寄养', '洗护');
UPDATE appointments SET merchant_id = 4 WHERE service_name = '问诊';
