-- 注意：数据库创建和切换已在 DbInitializer.java 中处理
-- 本文件只包含表结构和优化后的数据初始化语句

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20) UNIQUE COMMENT '手机号全局唯一（允许 NULL，未填手机号的账号可多个）',
    avatar_data LONGBLOB,
    avatar_type VARCHAR(50),
    role INT DEFAULT 0 COMMENT '0-普通用户, 1-管理员',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分类表
CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 商品表（已优化：使用image_data存储二进制图片）
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    image_data LONGBLOB,
    stock INT DEFAULT 0,
    category_id INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 购物车表
CREATE TABLE IF NOT EXISTS shopping_carts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_product (user_id, product_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    order_no VARCHAR(100) NOT NULL UNIQUE,
    total_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(50) DEFAULT '待付款' COMMENT '待付款, 已付款, 已发货, 已完成, 已取消',
    address VARCHAR(255),
    phone VARCHAR(20),
    receiver VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at DATETIME,
    user_name VARCHAR(100),
    product_name VARCHAR(500),
    INDEX idx_user (user_id),
    INDEX idx_order_no (order_no),
    INDEX idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 订单项表
CREATE TABLE IF NOT EXISTS order_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT DEFAULT 1,
    price DECIMAL(10,2) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 地址表
CREATE TABLE IF NOT EXISTS addresses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    province VARCHAR(50) NOT NULL,
    city VARCHAR(50) NOT NULL,
    district VARCHAR(50) NOT NULL,
    detail_address TEXT NOT NULL,
    is_default BOOLEAN DEFAULT false,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 订单评价表
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入测试数据

-- 用户数据
INSERT INTO users (username, password, email, phone, role) VALUES
('admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'admin@petshop.com', '13800138000', 1),
('user1', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'user1@petshop.com', '13800138001', 0);

-- 分类数据
INSERT INTO categories (name, sort_order) VALUES
('宠物食品', 1),
('玩具用品', 2),
('窝垫床品', 3),
('清洁护理', 4),
('医疗健康', 5),
('服饰配件', 6),
('宠物服务', 7),
('更多分类', 8);

-- 商品数据（精简到30条代表性样本，每个分类3-4条）
DELETE FROM products;

INSERT INTO products (name, description, price, stock, category_id) VALUES

-- 宠物食品 (分类1) - 4条
('狗粮 1kg', '优质犬粮，适合各年龄段狗狗，营养均衡', 59.90, 100, 1),
('猫粮 2kg', '营养均衡猫粮，添加鱼肉成分，猫咪最爱', 89.90, 80, 1),
('狗零食礼盒', '多种口味狗零食，训练奖励必备，含鸡肉牛肉等', 49.90, 200, 1),
('猫罐头 6罐装', '美味猫罐头，补充营养，汤汁浓郁', 79.90, 150, 1),

-- 玩具用品 (分类2) - 4条
('毛绒发声玩具', '耐咬毛绒玩具，内置发声器，吸引宠物注意力', 35.90, 180, 2),
('橡胶磨牙球', '天然橡胶材质，磨牙洁齿，安全无毒', 22.90, 250, 2),
('逗猫棒套装', '羽毛逗猫棒，多种颜色，猫咪爱玩', 19.90, 300, 2),
('猫抓板', '瓦楞纸材质猫抓板，保护家具，耐用', 29.90, 200, 2),

-- 窝垫床品 (分类3) - 3条
('四季通用狗窝', '保暖舒适，可拆洗设计，适合中小型犬', 89.90, 90, 3),
('猫窝', '半封闭猫窝，给猫咪安全感，四季可用', 59.90, 100, 3),
('宠物垫子', '防滑防水，易清洁，四季通用', 39.90, 150, 3),

-- 清洁护理 (分类4) - 4条
('宠物沐浴露', '温和配方，留香持久，适合各类宠物', 45.00, 120, 4),
('猫咪梳子', '去浮毛，按摩皮肤，减少掉毛', 25.90, 200, 4),
('狗狗指甲剪', '安全剪甲设计，防止流血，新手适用', 19.90, 250, 4),
('宠物湿巾', '100抽装，清洁抑菌，方便实用', 22.90, 300, 4),

-- 医疗健康 (分类5) - 4条
('体内驱虫药', '广谱驱虫，安全有效，适合猫狗', 68.00, 90, 5),
('体外驱虫滴剂', '驱杀跳蚤螨虫，每月一次，方便快捷', 79.00, 85, 5),
('宠物益生菌', '调理肠胃，改善腹泻，增强消化', 45.00, 150, 5),
('猫咪化毛膏', '帮助排毛，保护肠胃，减少毛球', 45.00, 120, 5),

-- 服饰配件 (分类6) - 4条
('狗狗雨衣', '防水透气，雨天必备，反光设计', 59.00, 70, 6),
('猫咪衣服', '秋冬保暖，可爱款式，多种颜色', 49.00, 80, 6),
('牵引绳套装', '伸缩牵引，舒适握把，耐用安全', 69.00, 100, 6),
('宠物项圈', '可调节，带名牌，安全可靠', 29.90, 200, 6),

-- 宠物服务 (分类7) - 3条
('宠物美容', '专业宠物美容服务，包含洗澡、剪毛等', 666.00, 100, 7),
('宠物寄养', '专业宠物寄养服务，24小时看护', 666.00, 50, 7),
('宠物训练', '专业宠物行为训练，纠正不良习惯', 9999.00, 30, 7),

-- 更多分类 (分类8) - 3条
('宠物饮水机', '自动循环活水，保持水质新鲜', 99.00, 60, 8),
('自动喂食器', '定时定量，出差必备，智能便捷', 149.00, 50, 8),
('外出猫包', '透气便携，出行方便，安全舒适', 69.00, 80, 8);

-- 测试地址数据
INSERT INTO addresses (user_id, name, phone, province, city, district, detail_address, is_default) VALUES
(2, '张三', '13800138000', '北京市', '北京市', '朝阳区', '建国路 88 号 SOHO 现代城', true),
(2, '李四', '13900139000', '上海市', '上海市', '浦东新区', '世纪大道 100 号环球金融中心', false);

-- 短信验证码表
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
