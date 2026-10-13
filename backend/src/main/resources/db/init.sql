-- 注意：数据库创建和切换已在 DbInitializer.java 中处理
-- 本文件只包含表结构和数据初始化语句

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100) UNIQUE,
    phone VARCHAR(20) UNIQUE COMMENT '手机号全局唯一（允许 NULL，未填手机号的账号可多个）',
    avatar VARCHAR(255),
    role INT DEFAULT 0 COMMENT '0-普通用户, 1-管理员',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 分类表
CREATE TABLE IF NOT EXISTS categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    parent_id INT DEFAULT 0,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 商品表
CREATE TABLE IF NOT EXISTS products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    image_url VARCHAR(255),
    image_data LONGBLOB COMMENT '商品主图二进制数据',
    img_data LONGBLOB COMMENT '商品备用图二进制数据',
    stock INT DEFAULT 0,
    category_id INT,
    merchant_id INT COMMENT '商家用户ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_merchant (merchant_id)
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
    merchant_id INT COMMENT '所属商家ID，NULL 表示自营',
    user_name VARCHAR(100) COMMENT '下单用户名快照',
    product_name VARCHAR(200) COMMENT '商品名快照',
    order_no VARCHAR(100) NOT NULL UNIQUE,
    total_price DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '优惠券抵扣金额',
    user_coupon_id INT COMMENT '本次订单使用的用户优惠券ID',
    pay_method VARCHAR(20) COMMENT '支付方式编码: WECHAT/ALIPAY/BANK/COD',
    paid_at DATETIME COMMENT '支付完成时间',
    status VARCHAR(50) DEFAULT '待付款' COMMENT '待付款, 已付款, 已发货, 已完成, 已取消',
    address VARCHAR(255),
    phone VARCHAR(20),
    receiver VARCHAR(100),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    expires_at DATETIME,
    INDEX idx_user (user_id),
    INDEX idx_merchant (merchant_id),
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

-- 插入测试数据
INSERT INTO users (username, password, email, phone, role) VALUES
('admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'admin@petshop.com', '13800138000', 1),
('user1', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'user1@petshop.com', '13800138001', 0);

-- 分类不变
INSERT INTO categories (name, description, sort_order) VALUES
('宠物食品', '各类宠物食品', 1),
('玩具用品', '宠物玩具、窝具等', 2),
('窝垫床品', '宠物窝垫、床品等', 3),
('清洁护理', '宠物清洁、护理用品', 4),
('医疗健康', '宠物药品和保健品', 5),
('服饰配件', '宠物服饰、配件', 6),
('宠物服务', '宠物相关服务', 7),
('更多分类', '其他宠物相关商品', 8);

DELETE FROM products;
 
 
 INSERT INTO products (name, description, price, image_url, stock, category_id) VALUES
 
 
 -- 宠物食品 (1)
 ('狗粮 1kg', '优质犬粮，适合各年龄段狗狗', 59.90, 'https://img11.360buyimg.com/n1/jfs/t1/19677/24/25446/31173/647d1253F2778c862/8975678d07080438.jpg', 100, 1),
 ('猫粮 2kg', '营养均衡猫粮，猫咪最爱', 89.90, 'https://img14.360buyimg.com/n1/jfs/t1/17414/36/27699/31317/643c725aFd1277567/85678d9087654321.jpg', 80, 1),
 ('狗零食礼盒', '多种口味狗零食，训练奖励必备', 49.90, 'https://img10.360buyimg.com/n1/jfs/t1/18659/23/26211/29876/648f2345F789d0123/456789abcdef0123.jpg', 200, 1),
 ('猫罐头 6罐装', '美味猫罐头，补充营养', 79.90, 'https://img12.360buyimg.com/n1/jfs/t1/19888/11/25123/32456/649a3456F890c1234/abcdef7890123456.jpg', 150, 1),
 ('狗粮 5kg', '大包装狗粮，性价比高', 229.00, 'https://img11.360buyimg.com/n1/jfs/t1/20765/36/24567/38765/646b4567F901d2345/1234567890abcdef.jpg', 60, 1),
 ('猫粮 5kg', '大包装猫粮，猫咪爱吃', 189.00, 'https://img14.360buyimg.com/n1/jfs/t1/17987/19/27123/35678/644c5678Fa12e3456/234567890bcdef01.jpg', 50, 1),
 ('狗零食磨牙棒', '耐咬磨牙棒，清洁牙齿', 25.90, 'https://img10.360buyimg.com/n1/jfs/t1/18234/26/26789/28765/647d6789Fb23f4567/34567890cdef0123.jpg', 300, 1),
 ('猫零食小鱼干', '新鲜小鱼干，猫咪最爱', 19.90, 'https://img12.360buyimg.com/n1/jfs/t1/19345/14/25678/31234/648e7890Fc34g5678/4567890def123456.jpg', 250, 1),
 ('宠物羊奶粉', '幼犬幼猫专用羊奶粉', 78.00, 'https://img11.360buyimg.com/n1/jfs/t1/20456/32/24890/36789/645f8901Fd45h6789/5678901ef2345678.jpg', 120, 1),
 ('狗粮幼犬粮', '幼犬专用粮，营养好吸收', 69.90, 'https://img14.360buyimg.com/n1/jfs/t1/17678/28/27456/33456/646a9012Fe56i7890/6789012fg3456789.jpg', 90, 1),
 ('猫粮幼猫粮', '幼猫专用粮，助力成长', 79.90, 'https://img10.360buyimg.com/n1/jfs/t1/18789/21/26345/30123/647b0123Ff67j8901/7890123gh4567890.jpg', 85, 1),
 ('老年犬粮', '老年犬专用粮，呵护健康', 75.90, 'https://img12.360buyimg.com/n1/jfs/t1/19567/17/25345/34567/648c1234Fg78k9012/8901234hi5678901.jpg', 80, 1),
 ('冻干鸡肉零食', '纯肉冻干，高蛋白营养', 59.90, 'https://img11.360buyimg.com/n1/jfs/t1/20234/34/24678/37890/645d2345Fh89l0123/9012345ij6789012.jpg', 180, 1),
 ('猫咪化毛膏', '帮助排毛，保护肠胃', 45.00, 'https://img14.360buyimg.com/n1/jfs/t1/17890/24/27234/32345/646e3456Fi90m1234/0123456jk7890123.jpg', 130, 1),
 
 
 -- 玩具用品 (2)
 ('毛绒发声玩具', '耐咬毛绒玩具，发声吸引', 35.90, 'https://img12.360buyimg.com/n1/jfs/t1/19678/19/25123/35678/648f4567Fj01n2345/1234567kl8901234.jpg', 180, 2),
 ('橡胶磨牙球', '天然橡胶，磨牙洁齿', 22.90, 'https://img10.360buyimg.com/n1/jfs/t1/18345/27/26678/29876/647a5678Fk12o3456/2345678mn9012345.jpg', 250, 2),
 ('逗猫棒套装', '羽毛逗猫棒，猫咪爱玩', 19.90, 'https://img11.360buyimg.com/n1/jfs/t1/20567/31/24789/36567/645b6789Fl23p4567/3456789no0123456.jpg', 300, 2),
 ('猫抓板', '瓦楞纸猫抓板，保护家具', 29.90, 'https://img14.360buyimg.com/n1/jfs/t1/17789/29/27345/33234/646c7890Fm34q5678/4567890op1234567.jpg', 200, 2),
 ('飞盘', '耐咬飞盘，互动训练', 28.00, 'https://img12.360buyimg.com/n1/jfs/t1/19456/16/25456/34345/648d8901Fn45r6789/5678901pq2345678.jpg', 150, 2),
 ('漏食球', '益智漏食，消磨时间', 32.90, 'https://img10.360buyimg.com/n1/jfs/t1/18123/25/26890/28654/647e9012Fo56s7890/6789012qr3456789.jpg', 170, 2),
 
 
 -- 窝垫床品 (3)
 ('四季通用狗窝', '保暖舒适，可拆洗', 89.90, 'https://img11.360buyimg.com/n1/jfs/t1/20345/33/24567/37654/645f0123Fp67t8901/7890123st4567890.jpg', 90, 3),
 ('猫窝', '半封闭猫窝，安全感', 59.90, 'https://img14.360buyimg.com/n1/jfs/t1/17567/22/27567/32123/646a1234Fq78u9012/8901234tu5678901.jpg', 100, 3),
 ('宠物垫子', '防滑防水，易清洁', 39.90, 'https://img12.360buyimg.com/n1/jfs/t1/19234/13/25789/35432/648b2345Fr89v0123/9012345uv6789012.jpg', 150, 3),
 ('加热狗床', '冬季保暖，恒温舒适', 129.00, 'https://img10.360buyimg.com/n1/jfs/t1/18678/28/26234/29543/647c3456Fs90w1234/0123456vw7890123.jpg', 40, 3),
 
 
 -- 清洁护理 (4)
 ('宠物沐浴露', '温和配方，留香持久', 45.00, 'https://img11.360buyimg.com/n1/jfs/t1/20123/35/24890/36432/645d4567Ft01x2345/1234567wx8901234.jpg', 120, 4),
 ('猫咪梳子', '去浮毛，按摩皮肤', 25.90, 'https://img14.360buyimg.com/n1/jfs/t1/17456/23/27678/31012/646e5678Fu12y3456/2345678xy9012345.jpg', 200, 4),
 ('狗狗指甲剪', '安全剪甲，防止流血', 19.90, 'https://img12.360buyimg.com/n1/jfs/t1/19789/18/25234/34210/648f6789Fv23z4567/3456789yz0123456.jpg', 250, 4),
 ('宠物湿巾', '100抽，清洁抑菌', 22.90, 'https://img10.360buyimg.com/n1/jfs/t1/18567/26/26345/28431/647a7890Fw34A5678/4567890AB1234567.jpg', 300, 4),
 ('干洗粉', '免洗清洁，幼犬幼猫适用', 38.00, 'https://img11.360buyimg.com/n1/jfs/t1/20678/30/24678/35321/645b8901Fx45B6789/5678901BC2345678.jpg', 80, 4),
 
 
 -- 医疗健康 (5)
 ('体内驱虫药', '广谱驱虫，安全有效', 68.00, 'https://img14.360buyimg.com/n1/jfs/t1/17345/21/27789/30897/646c9012Fy56C7890/6789012CD3456789.jpg', 90, 5),
 ('体外驱虫滴剂', '驱杀跳蚤螨虫', 79.00, 'https://img12.360buyimg.com/n1/jfs/t1/19123/15/25890/33765/648d0123Fz67D8901/7890123DE4567890.jpg', 85, 5),
 ('宠物益生菌', '调理肠胃，改善腹泻', 45.00, 'https://img10.360buyimg.com/n1/jfs/t1/18456/24/26456/27654/647e1234FA78E9012/8901234EF5678901.jpg', 150, 5),
 ('创可贴', '宠物专用，止血消炎', 12.90, 'https://img11.360buyimg.com/n1/jfs/t1/20234/36/24789/34543/645f2345FB89F0123/9012345FG6789012.jpg', 400, 5),
 ('眼药水', '清洁眼部，去泪痕', 35.00, 'https://img14.360buyimg.com/n1/jfs/t1/17234/25/27890/29432/646a3456FC90G1234/0123456GH7890123.jpg', 120, 5),
 
 
 -- 服饰配件 (6)
 ('狗狗雨衣', '防水透气，雨天必备', 59.00, 'https://img12.360buyimg.com/n1/jfs/t1/19567/12/25345/32654/648b4567FD01H2345/1234567HI8901234.jpg', 70, 6),
 ('猫咪衣服', '秋冬保暖，可爱款式', 49.00, 'https://img10.360buyimg.com/n1/jfs/t1/18234/27/26789/26543/647c5678FE12I3456/2345678IJ9012345.jpg', 80, 6),
 ('牵引绳套装', '伸缩牵引，舒适握把', 69.00, 'https://img11.360buyimg.com/n1/jfs/t1/20456/32/24567/33432/645d6789FF23J4567/3456789JK0123456.jpg', 100, 6),
 ('宠物项圈', '可调节，带名牌', 29.90, 'https://img14.360buyimg.com/n1/jfs/t1/17123/26/27901/28321/646e7890FG34K5678/4567890KL1234567.jpg', 200, 6),
 ('狗狗鞋子', '防滑耐磨，保护脚掌', 45.00, 'https://img12.360buyimg.com/n1/jfs/t1/19345/14/25678/31543/648f8901FH45L6789/5678901LM2345678.jpg', 90, 6),
 
 
 -- 宠物服务 (7)
('宠物美容', '专业宠物美容服务，包含洗澡、剪毛等', 666.00, 'https://neeko-copilot.bytedance.net/api/text_to_image?prompt=cute%20cat%20wearing%20red%20glasses%20portrait&image_size=square', 100, 7),
('宠物寄养', '专业宠物寄养服务，24小时看护', 666.00, 'https://neeko-copilot.bytedance.net/api/text_to_image?prompt=happy%20puppy%20playing%20in%20yard&image_size=square', 50, 7),
('宠物训练', '专业宠物行为训练，纠正不良习惯', 9999.00, 'https://neeko-copilot.bytedance.net/api/text_to_image?prompt=dog%20obedience%20training%20session&image_size=square', 30, 7),

-- 更多分类 (8)
('宠物饮水机', '自动循环活水更健康', 99.00, 'https://img10.360buyimg.com/n1/jfs/t1/18789/22/26234/25432/647a9012FI56M7890/6789012MN3456789.jpg', 60, 8),
('自动喂食器', '定时定量，出差必备', 149.00, 'https://img11.360buyimg.com/n1/jfs/t1/20567/31/24789/32321/645b0123FJ67N8901/7890123NO4567890.jpg', 50, 8),
 ('折叠狗笼', '便携耐用，易收纳', 199.00, 'https://img14.360buyimg.com/n1/jfs/t1/17678/28/27456/27210/646c1234FK78O9012/8901234OP5678901.jpg', 40, 8),
 ('外出猫包', '透气便携，出行方便', 69.00, 'https://img12.360buyimg.com/n1/jfs/t1/19678/19/25123/30109/648d2345FL89P0123/9012345PQ6789012.jpg', 80, 8),
 ('宠物航空箱', '航空标准，安全出行', 129.00, 'https://img10.360buyimg.com/n1/jfs/t1/18123/25/26890/26098/647e3456FM90Q1234/0123456QR7890123.jpg', 60, 8),
 
 -- 更多商品
 ('猫咪玩具老鼠', '仿真老鼠，带猫薄荷', 15.90, 'https://img11.360buyimg.com/n1/jfs/t1/20890/18/23456/28765/649c1234FA12B3456/1234567AB8901234.jpg', 350, 2),
 ('狗狗牵引背心', '舒适透气，防爆冲', 79.00, 'https://img14.360buyimg.com/n1/jfs/t1/17234/22/28901/31234/647d2345FB23C4567/2345678BC9012345.jpg', 110, 6),
 ('宠物尿垫', '加厚吸水，除臭抑菌', 39.90, 'https://img12.360buyimg.com/n1/jfs/t1/19456/15/26789/27654/648e3456FC34D5678/3456789CD0123456.jpg', 280, 8),
 ('猫砂盆', '半封闭式，防外溅', 89.00, 'https://img10.360buyimg.com/n1/jfs/t1/18567/29/27123/30123/646f4567FD45E6789/4567890DE1234567.jpg', 75, 8),
 ('膨润土猫砂', '结团好，低粉尘', 29.90, 'https://img11.360buyimg.com/n1/jfs/t1/20123/33/24890/26543/645a5678FE56F7890/5678901EF2345678.jpg', 400, 8),
 ('狗狗磨牙骨头', '牛皮材质，耐咬', 35.00, 'https://img14.360buyimg.com/n1/jfs/t1/17890/26/28234/29876/647b6789FF67G8901/6789012FG3456789.jpg', 220, 1),
 ('宠物营养膏', '增强免疫力，补充维生素', 55.00, 'https://img12.360buyimg.com/n1/jfs/t1/19234/17/25678/32345/648c7890FG78H9012/7890123GH4567890.jpg', 140, 5),
 ('猫咪逗猫隧道', '折叠设计，趣味互动', 42.00, 'https://img10.360buyimg.com/n1/jfs/t1/18345/23/26567/28432/647d8901FH89I0123/8901234HI5678901.jpg', 160, 2),
 ('狗狗保暖衣服', '加绒加厚，冬季必备', 68.00, 'https://img11.360buyimg.com/n1/jfs/t1/20678/35/23789/33456/645e9012FI90J1234/9012345IJ6789012.jpg', 95, 6),
 ('宠物除臭喷雾', '天然配方，快速除味', 32.00, 'https://img14.360buyimg.com/n1/jfs/t1/17567/20/27890/25678/646f0123FJ01K2345/0123456JK7890123.jpg', 200, 4),
 ('自动激光逗猫器', '智能旋转，解放双手', 59.00, 'https://img12.360buyimg.com/n1/jfs/t1/19789/16/25234/31234/648a1234FK12L3456/1234567KL8901234.jpg', 130, 2),
 ('狗狗雨衣四脚款', '全身防水，反光条设计', 75.00, 'https://img10.360buyimg.com/n1/jfs/t1/18234/28/26345/27890/647b2345FL23M4567/2345678LM9012345.jpg', 85, 6),
 ('宠物电动牙刷', '超声波清洁，保护牙齿', 89.00, 'https://img11.360buyimg.com/n1/jfs/t1/20345/30/24567/34567/645c3456FM34N5678/3456789MN0123456.jpg', 70, 4),
 ('猫爬架', '多层设计，磨爪休息', 199.00, 'https://img14.360buyimg.com/n1/jfs/t1/17123/24/28123/36789/646d4567FN45O6789/4567890NO1234567.jpg', 35, 8),
 ('狗狗训练零食', '低卡路里，奖励专用', 28.00, 'https://img12.360buyimg.com/n1/jfs/t1/19567/13/25890/24567/648e5678FO56P7890/5678901OP2345678.jpg', 260, 1),
 ('宠物体温计', '红外测温，快速准确', 45.00, 'https://img10.360buyimg.com/n1/jfs/t1/18678/21/26789/26789/647f6789FP67Q8901/6789012PQ3456789.jpg', 110, 5),
 ('猫咪伊丽莎白圈', '柔软舒适，防舔舐', 22.00, 'https://img11.360buyimg.com/n1/jfs/t1/20234/37/24123/23456/645a7890FQ78R9012/7890123QR4567890.jpg', 180, 5),

-- 宠物食品 (1) 新增
('狗狗罐头 12罐装', '营养丰富，多种口味', 129.90, 'https://img11.360buyimg.com/n1/jfs/t1/20890/18/23456/32765/649c1234FA12B3456/1234567AB8901234.jpg', 120, 1),
('猫咪冻干混合装', '多种肉类，营养均衡', 89.90, 'https://img14.360buyimg.com/n1/jfs/t1/17234/22/28901/35234/647d2345FB23C4567/2345678BC9012345.jpg', 90, 1),
('狗狗鲜粮', '新鲜制作，无添加剂', 159.90, 'https://img12.360buyimg.com/n1/jfs/t1/19456/15/26789/31654/648e3456FC34D5678/3456789CD0123456.jpg', 60, 1),
('猫咪营养膏', '补充营养，增强免疫力', 68.00, 'https://img10.360buyimg.com/n1/jfs/t1/18567/29/27123/33123/646f4567FD45E6789/4567890DE1234567.jpg', 130, 1),
('狗狗羊奶酪零食', '补钙美毛，训练奖励', 39.90, 'https://img11.360buyimg.com/n1/jfs/t1/20123/33/24890/30543/645a5678FE56F7890/5678901EF2345678.jpg', 200, 1),
('猫咪化毛球片', '帮助排毛，预防毛球', 45.00, 'https://img14.360buyimg.com/n1/jfs/t1/17890/26/28234/32876/647b6789FF67G8901/6789012FG3456789.jpg', 150, 1),

-- 玩具用品 (2) 新增
('狗狗发声骨头', '耐咬发声，互动玩具', 29.90, 'https://img12.360buyimg.com/n1/jfs/t1/19234/17/25678/35345/648c7890FG78H9012/7890123GH4567890.jpg', 220, 2),
('猫咪电动玩具', '自动逗猫，解放双手', 79.00, 'https://img10.360buyimg.com/n1/jfs/t1/18345/23/26567/31432/647d8901FH89I0123/8901234HI5678901.jpg', 80, 2),
('狗狗飞盘软胶', '安全耐咬，互动训练', 35.00, 'https://img11.360buyimg.com/n1/jfs/t1/20678/35/23789/36456/645e9012FI90J1234/9012345IJ6789012.jpg', 150, 2),
('猫咪羽毛逗猫棒', '天然羽毛，吸引猫咪', 15.90, 'https://img14.360buyimg.com/n1/jfs/t1/17567/20/27890/28678/646f0123FJ01K2345/0123456JK7890123.jpg', 300, 2),
('狗狗拔河玩具', '加强互动，增进感情', 28.90, 'https://img12.360buyimg.com/n1/jfs/t1/19789/16/25234/34234/648a1234FK12L3456/1234567KL8901234.jpg', 180, 2),
('猫咪隧道玩具', '折叠设计，趣味互动', 49.90, 'https://img10.360buyimg.com/n1/jfs/t1/18234/28/26345/30890/647b2345FL23M4567/2345678LM9012345.jpg', 120, 2),

-- 窝垫床品 (3) 新增
('四季通用猫窝', '可拆洗，舒适透气', 79.90, 'https://img11.360buyimg.com/n1/jfs/t1/20345/30/24567/37567/645c3456FM34N5678/3456789MN0123456.jpg', 100, 3),
('狗狗行军床', '离地防潮，舒适透气', 129.90, 'https://img14.360buyimg.com/n1/jfs/t1/17123/24/28123/40789/646d4567FN45O6789/4567890NO1234567.jpg', 60, 3),
('宠物保暖垫', '珊瑚绒材质，保暖舒适', 49.90, 'https://img12.360buyimg.com/n1/jfs/t1/19567/13/25890/30567/648e5678FO56P7890/5678901OP2345678.jpg', 180, 3),
('猫咪吊床', '悬挂设计，节省空间', 59.90, 'https://img10.360buyimg.com/n1/jfs/t1/18678/21/26789/32789/647f6789FP67Q8901/6789012PQ3456789.jpg', 90, 3),

-- 清洁护理 (4) 新增
('宠物干洗泡沫', '免水洗，清洁方便', 45.00, 'https://img11.360buyimg.com/n1/jfs/t1/20234/37/24123/32456/645a7890FQ78R9012/7890123QR4567890.jpg', 150, 4),
('狗狗洗脚杯', '轻松洗脚，防止脏污', 39.90, 'https://img14.360buyimg.com/n1/jfs/t1/17987/19/27123/34678/644c5678Fa12e3456/234567890bcdef01.jpg', 120, 4),
('宠物梳子套装', '多种梳齿，适合不同毛发', 59.90, 'https://img12.360buyimg.com/n1/jfs/t1/19345/14/25678/34234/648e7890Fc34g5678/4567890def123456.jpg', 100, 4),
('宠物牙刷牙膏套装', '清洁口腔，预防牙结石', 39.90, 'https://img11.360buyimg.com/n1/jfs/t1/20456/32/24890/39789/645f8901Fd45h6789/5678901ef2345678.jpg', 180, 4),

-- 医疗健康 (5) 新增
('宠物耳螨药', '快速除螨，舒缓瘙痒', 58.00, 'https://img14.360buyimg.com/n1/jfs/t1/17678/28/27456/36456/646a9012Fe56i7890/6789012fg3456789.jpg', 100, 5),
('宠物营养补充剂', '补充多种维生素和矿物质', 68.00, 'https://img10.360buyimg.com/n1/jfs/t1/18789/21/26345/33123/647b0123Ff67j8901/7890123gh4567890.jpg', 120, 5),
('宠物伤口喷雾', '快速止血，促进愈合', 45.00, 'https://img12.360buyimg.com/n1/jfs/t1/19567/17/25345/37567/648c1234Fg78k9012/8901234hi5678901.jpg', 150, 5),
('宠物维生素片', '增强免疫力，促进健康', 39.90, 'https://img11.360buyimg.com/n1/jfs/t1/20234/34/24678/39890/645d2345Fh89l0123/9012345ij6789012.jpg', 200, 5),

-- 服饰配件 (6) 新增
('狗狗反光背心', '夜间出行，安全保障', 49.90, 'https://img14.360buyimg.com/n1/jfs/t1/17414/36/27699/34317/643c725aFd1277567/85678d9087654321.jpg', 150, 6),
('猫咪项圈铃铛', '可爱铃铛，防止丢失', 25.90, 'https://img10.360buyimg.com/n1/jfs/t1/18659/23/26211/32876/648f2345F789d0123/456789abcdef0123.jpg', 250, 6),
('狗狗背包', '便携背包，出行必备', 89.90, 'https://img12.360buyimg.com/n1/jfs/t1/19888/11/25123/35456/649a3456F890c1234/abcdef7890123456.jpg', 80, 6),
('宠物太阳镜', '防紫外线，保护眼睛', 59.90, 'https://img11.360buyimg.com/n1/jfs/t1/20765/36/24567/41765/646b4567F901d2345/1234567890abcdef.jpg', 60, 6),

-- 更多分类 (8) 新增
('宠物烘干箱', '快速烘干，避免着凉', 399.00, 'https://img14.360buyimg.com/n1/jfs/t1/17987/19/27123/38678/644c5678Fa12e3456/234567890bcdef01.jpg', 30, 8),
('宠物摄像头', '远程监控，实时查看', 299.00, 'https://img12.360buyimg.com/n1/jfs/t1/19345/14/25678/34234/648e7890Fc34g5678/4567890def123456.jpg', 40, 8),
('宠物航空箱', '安全出行，符合航空标准', 159.90, 'https://img11.360buyimg.com/n1/jfs/t1/20456/32/24890/39789/645f8901Fd45h6789/5678901ef2345678.jpg', 50, 8),
('宠物尿盆', '自动清洁，减少异味', 299.00, 'https://img14.360buyimg.com/n1/jfs/t1/17678/28/27456/36456/646a9012Fe56i7890/6789012fg3456789.jpg', 25, 8),
('宠物智能项圈', '定位追踪，健康监测', 399.00, 'https://img10.360buyimg.com/n1/jfs/t1/18789/21/26345/36123/647b0123Ff67j8901/7890123gh4567890.jpg', 30, 8),

-- 继续添加更多商品
-- 宠物食品 (1) 更多
('狗狗奶酪零食', '补钙又美味，训练奖励必备', 42.90, 'https://img11.360buyimg.com/n1/jfs/t1/21234/19/22345/29765/650c2345FA12B3457/2234567AB8901235.jpg', 220, 1),
('猫咪三文鱼罐头', '深海三文鱼，富含Omega-3', 35.90, 'https://img14.360buyimg.com/n1/jfs/t1/18345/23/27890/32234/648d3456FB23C4568/3345678BC9012346.jpg', 180, 1),
('狗狗鸡胸肉干', '纯手工制作，无添加剂', 55.90, 'https://img12.360buyimg.com/n1/jfs/t1/20567/16/25678/27654/649e4567FC34D5679/4456789CD0123457.jpg', 150, 1),
('猫咪营养餐包', '湿粮易消化，老年猫适用', 28.90, 'https://img10.360buyimg.com/n1/jfs/t1/19678/29/26098/30123/647f5678FD45E6790/5567890DE1234568.jpg', 200, 1),
('狗狗牛膝盖骨', '天然磨牙，清洁牙垢', 38.90, 'https://img11.360buyimg.com/n1/jfs/t1/20890/35/23456/34567/645a6789FE56F7801/6678901EF2345679.jpg', 180, 1),
('猫咪木天蓼棒', '吸引猫咪，娱乐磨爪', 18.90, 'https://img14.360buyimg.com/n1/jfs/t1/18901/27/26901/31234/647b7890FF67G8902/7789012FG3456790.jpg', 250, 1),
('狗狗酸奶溶豆', '奶香浓郁，幼犬最爱', 29.90, 'https://img12.360buyimg.com/n1/jfs/t1/19234/18/24567/32345/648c8901FG78H9013/8890123GH4567801.jpg', 190, 1),
('猫咪银鱼干', '高蛋白低脂肪，营养美味', 32.00, 'https://img10.360buyimg.com/n1/jfs/t1/18456/24/25456/28432/647d9012FH89I0124/9901234HI5678902.jpg', 210, 1),
('狗狗蔬菜饼干', '添加蔬菜，营养均衡', 25.90, 'https://img11.360buyimg.com/n1/jfs/t1/20789/36/23678/33456/645e0123FI90J1235/0012345IJ6789013.jpg', 170, 1),
('猫咪起司条', '美味起司，猫咪无法抗拒', 22.90, 'https://img14.360buyimg.com/n1/jfs/t1/17678/21/27123/28678/646f1234FJ01K2346/1123456JK7890124.jpg', 230, 1),
('狗狗鸭肉卷', '清热降火，夏日必备', 45.90, 'https://img12.360buyimg.com/n1/jfs/t1/19890/17/25123/34234/648a2345FK12L3457/2234567KL8901235.jpg', 160, 1),
('猫咪扇贝干', '新鲜海鲜，口感鲜美', 38.00, 'https://img10.360buyimg.com/n1/jfs/t1/18345/28/26234/30890/647b3456FL23M4568/3345678LM9012346.jpg', 180, 1),

-- 玩具用品 (2) 更多
('狗狗拔河绳结', '结实耐用，增加互动', 32.90, 'https://img11.360buyimg.com/n1/jfs/t1/20567/30/24567/36567/645c4567FM34N5670/4456789MN0123457.jpg', 200, 2),
('猫咪电动老鼠', '智能跑动，激发捕猎本能', 68.00, 'https://img14.360buyimg.com/n1/jfs/t1/18234/24/27234/37234/646d5678FN45O6781/5567890NO1234568.jpg', 90, 2),
('狗狗发声球', '弹性十足，吸引注意力', 26.90, 'https://img12.360buyimg.com/n1/jfs/t1/19678/19/25345/29876/648e6789FO56P7891/6678901OP2345670.jpg', 240, 2),
('猫咪逗猫手套', '互动逗猫，增进感情', 35.00, 'https://img10.360buyimg.com/n1/jfs/t1/18456/27/26345/28654/647f7890FP67Q8902/7789012PQ3456781.jpg', 150, 2),
('狗狗训练飞盘', '柔软不伤嘴，户外必备', 38.00, 'https://img11.360buyimg.com/n1/jfs/t1/20890/19/23456/35789/645a8901FQ78R9014/8890123QR4567802.jpg', 130, 2),
('猫咪转盘球', '自动旋转，自嗨神器', 45.90, 'https://img14.360buyimg.com/n1/jfs/t1/17345/22/28123/31234/646b9012FR89S0125/9901234RS5678903.jpg', 100, 2),
('狗狗投掷球', '弹性好，训练捡拾', 22.90, 'https://img12.360buyimg.com/n1/jfs/t1/19567/16/25234/28765/648c0123FS90T1236/0012345ST6789014.jpg', 260, 2),
('猫咪薄荷玩具', '天然薄荷，吸引猫咪', 18.90, 'https://img10.360buyimg.com/n1/jfs/t1/18789/25/26456/29345/647d1234FT01U2347/1123456TU7890125.jpg', 280, 2),
('狗狗漏食玩具', '益智消磨，防止拆家', 55.00, 'https://img11.360buyimg.com/n1/jfs/t1/20123/34/24789/34678/645e2345FU12V3458/2234567UV8901236.jpg', 110, 2),
('猫咪爬树玩具', '多层设计，趣味十足', 89.90, 'https://img14.360buyimg.com/n1/jfs/t1/17890/26/27345/31567/646f3456FV23W4569/3345678VW9012347.jpg', 70, 2),

-- 窝垫床品 (3) 更多
('四季通用狗窝', '加厚保暖，四季可用', 129.90, 'https://img12.360buyimg.com/n1/jfs/t1/19456/15/25456/35678/648a4567FW34X5670/4456789WX0123458.jpg', 80, 3),
('猫咪蒙古包', '半封闭设计，猫咪喜欢', 69.90, 'https://img10.360buyimg.com/n1/jfs/t1/18678/28/26234/28432/647b5678FX45Y6781/5567890XY1234569.jpg', 120, 3),
('狗狗珊瑚绒床', '柔软舒适，保暖透气', 99.90, 'https://img11.360buyimg.com/n1/jfs/t1/20567/32/24567/37567/645c6789FY56Z7892/6678901YZ2345670.jpg', 90, 3),
('猫咪豆袋椅', '懒人沙发，舒适度高', 159.00, 'https://img14.360buyimg.com/n1/jfs/t1/17123/24/27234/32654/646d7890FZ67A8903/7789012ZA3456781.jpg', 50, 3),
('狗狗豪华别墅', '大型犬专属，空间宽敞', 299.00, 'https://img12.360buyimg.com/n1/jfs/t1/19345/14/25345/33789/648f8901FA78B9015/8890123AB4567803.jpg', 30, 3),
('猫咪毛绒窝', '毛绒材质，冬天保暖', 59.90, 'https://img10.360buyimg.com/n1/jfs/t1/18234/27/26123/29432/647a9012FB89C0126/9901234BC5678904.jpg', 110, 3),
('狗狗凉垫', '冰丝凉感，夏日消暑', 79.90, 'https://img11.360buyimg.com/n1/jfs/t1/20678/35/23789/34678/645b0123FC90D1237/0012345CD6789015.jpg', 85, 3),
('猫咪窗台窝', '挂在窗户上，晒太阳专用', 89.90, 'https://img14.360buyimg.com/n1/jfs/t1/17678/29/27123/31678/646c1234FD01E2348/1123456DE7890126.jpg', 75, 3),

-- 清洁护理 (4) 更多
('宠物除毛刷', '轻松去除浮毛，方便实用', 39.90, 'https://img12.360buyimg.com/n1/jfs/t1/19567/13/25234/32876/648d2345FE12F3459/2234567EF8901237.jpg', 180, 4),
('猫咪洗澡刷', '硅胶材质，清洁按摩', 28.90, 'https://img10.360buyimg.com/n1/jfs/t1/18789/22/26234/28654/647e3456FF23G4560/3345678FG9012348.jpg', 220, 4),
('狗狗洗耳液', '清洁耳道，预防耳螨', 35.00, 'https://img11.360buyimg.com/n1/jfs/t1/20234/36/24567/35789/645f4567FG34H5671/4456789GH0123459.jpg', 140, 4),
('宠物护毛素', '深层滋养，毛发柔顺', 45.00, 'https://img14.360buyimg.com/n1/jfs/t1/17345/21/27123/31678/646a5678FH45I6782/5567890HI1234560.jpg', 130, 4),
('猫咪指甲锉', '光滑指甲，防止刮伤', 22.90, 'https://img12.360buyimg.com/n1/jfs/t1/19678/18/25345/28654/648b6789FI56J7893/6678901IJ2345671.jpg', 200, 4),
('宠物口腔清洁喷雾', '快速清新口气，抑制细菌', 38.00, 'https://img10.360buyimg.com/n1/jfs/t1/18456/25/26234/29678/647c7890FJ67K8904/7789012JK3456782.jpg', 160, 4),
('狗狗宠物毛巾', '超强吸水，柔软舒适', 29.90, 'https://img11.360buyimg.com/n1/jfs/t1/20890/37/23456/34789/645d8901FK78L9016/8890123KL4567804.jpg', 190, 4),
('猫咪屁屁湿巾', '专用清洁，保持卫生', 25.90, 'https://img14.360buyimg.com/n1/jfs/t1/17234/23/27234/30678/646f9012FL89M0127/9901234LM5678905.jpg', 170, 4),

-- 医疗健康 (5) 更多
('宠物感冒药', '治疗感冒咳嗽，缓解症状', 42.00, 'https://img12.360buyimg.com/n1/jfs/t1/19567/16/25123/31789/648a0123FM90N1238/0012345MN6789016.jpg', 110, 5),
('猫咪应激灵', '缓解紧张情绪，预防应激', 58.00, 'https://img10.360buyimg.com/n1/jfs/t1/18234/28/26234/28765/647b2345FN45O4562/1123456NO7890127.jpg', 90, 5),
('狗狗关节保健', '保护关节，预防关节炎', 128.00, 'https://img11.360buyimg.com/n1/jfs/t1/20678/34/24567/35890/645c3456FO56P5673/2234567OP8901238.jpg', 80, 5),
('宠物皮肤喷雾', '治疗皮肤病，快速止痒', 65.00, 'https://img14.360buyimg.com/n1/jfs/t1/17890/25/27123/32876/646d4567FP67Q6784/3345678PQ9012349.jpg', 100, 5),
('猫咪滴眼液', '清洁眼睛，治疗结膜炎', 32.00, 'https://img12.360buyimg.com/n1/jfs/t1/19345/17/25234/29654/648e5678FQ78R8905/4456789QR0123450.jpg', 140, 5),
('宠物益生菌粉', '调理肠胃，改善消化', 55.00, 'https://img10.360buyimg.com/n1/jfs/t1/18789/26/26123/30678/647f6789FR89S9017/5567890RS2345672.jpg', 120, 5),
('狗狗钙片', '补钙壮骨，强健骨骼', 45.00, 'https://img11.360buyimg.com/n1/jfs/t1/20123/33/24678/34789/645a7890FS90T0128/6678901ST3456783.jpg', 150, 5),
('宠物消毒喷雾', '杀菌消毒，预防疾病', 28.90, 'https://img14.360buyimg.com/n1/jfs/t1/17567/22/27123/31789/646b8901FT01U2349/7789012TU4567894.jpg', 200, 5),

-- 服饰配件 (6) 更多
('狗狗圣诞服装', '节日装饰，拍照必备', 59.90, 'https://img12.360buyimg.com/n1/jfs/t1/19678/19/25234/32876/648c9012FU12V3450/8890123UV5678905.jpg', 120, 6),
('猫咪蝴蝶结', '可爱装饰，拍照上镜', 22.90, 'https://img10.360buyimg.com/n1/jfs/t1/18456/27/26123/28765/647d0123FV23W4561/9901234VW6789016.jpg', 250, 6),
('狗狗西服套装', '绅士风格，特殊场合', 89.90, 'https://img11.360buyimg.com/n1/jfs/t1/20789/35/24567/35890/645e2345FW34X5672/0012345WX7890127.jpg', 80, 6),
('猫咪衣服小裙子', '甜美可爱，适合夏天', 45.90, 'https://img14.360buyimg.com/n1/jfs/t1/17345/24/27123/31789/646f3456FX45Y6783/1123456XY8901238.jpg', 130, 6),
('狗狗围巾', '保暖时尚，冬日必备', 39.90, 'https://img12.360buyimg.com/n1/jfs/t1/19567/15/25234/29654/648a4567FY56Z7894/2234567YZ0123451.jpg', 160, 6),
('猫咪生日帽', '生日派对，节日装饰', 18.90, 'https://img10.360buyimg.com/n1/jfs/t1/18789/26/26123/30678/647b5678FZ67A8906/3345678ZA1234562.jpg', 180, 6),
('狗狗帽子', '遮阳防晒，时尚潮流', 35.00, 'https://img11.360buyimg.com/n1/jfs/t1/20234/37/24678/34890/645c6789FA78B9018/4456789AB2345673.jpg', 140, 6),
('猫咪领结', '绅士风格，可爱大方', 25.90, 'https://img14.360buyimg.com/n1/jfs/t1/17678/28/27123/31890/646d7890FB89C0129/5567890BC3456784.jpg', 200, 6),

-- 更多分类 (8) 更多
('宠物自动饮水器', '循环过滤，保持水质新鲜', 129.00, 'https://img12.360buyimg.com/n1/jfs/t1/19456/16/25234/32876/648e8901FC90D1230/6678901CD4567895.jpg', 70, 8),
('宠物智能喂食器', '手机控制，定时定量', 259.00, 'https://img10.360buyimg.com/n1/jfs/t1/18678/27/26123/29765/647f9012FD01E2340/7789012DE5678906.jpg', 45, 8),
('宠物外出背包', '通风透气，携带方便', 89.90, 'https://img11.360buyimg.com/n1/jfs/t1/20890/38/23456/35901/645a0123FE12F3451/8890123EF6789017.jpg', 85, 8),
('宠物折叠碗', '伸缩设计，外出便携', 29.90, 'https://img14.360buyimg.com/n1/jfs/t1/17234/25/27123/30876/646b1234FF23G4562/9901234FG5678908.jpg', 180, 8),
('宠物车载笼', '出行安全，舒适宽敞', 299.00, 'https://img12.360buyimg.com/n1/jfs/t1/19567/14/25234/31890/648c2345FG34H5673/0012345GH0123452.jpg', 35, 8),
('宠物拾便器', '方便卫生，遛狗必备', 35.00, 'https://img10.360buyimg.com/n1/jfs/t1/18345/26/26123/28765/647d3456FH45I6784/1123456HI7890129.jpg', 150, 8),
('宠物身份牌', '定制刻字，防止丢失', 25.90, 'https://img11.360buyimg.com/n1/jfs/t1/20678/36/24567/34901/645e4567FI56J7895/2234567IJ8901230.jpg', 220, 8),
('宠物推车', '老年宠物出行必备', 399.00, 'https://img14.360buyimg.com/n1/jfs/t1/17890/27/27123/31902/646f5678FJ67K8907/3345678JK9012341.jpg', 25, 8),

-- 水族用品 (新增分类8下的商品)
('鱼缸过滤器', '静音过滤，保持水质清洁', 129.00, 'https://img12.360buyimg.com/n1/jfs/t1/19345/15/25234/32901/648f8901FK78L9018/4456789KL2345675.jpg', 50, 8),
('鱼缸加热棒', '恒温控制，适合热带鱼', 58.00, 'https://img10.360buyimg.com/n1/jfs/t1/18789/23/26123/29876/647a9012FL89M0120/5567890LM3456786.jpg', 80, 8),
('鱼缸灯', '水草生长必备，照明装饰', 79.00, 'https://img11.360buyimg.com/n1/jfs/t1/20123/34/24678/35012/645b0123FM90N1231/6678901MN4567897.jpg', 65, 8),
('鱼粮', '营养均衡，适合各类观赏鱼', 35.00, 'https://img14.360buyimg.com/n1/jfs/t1/17567/21/27123/30987/646c1234FN45O4563/7789012NO5678908.jpg', 120, 8),
('鱼缸装饰', '仿真水草，布置美观', 45.90, 'https://img12.360buyimg.com/n1/jfs/t1/19678/18/25234/30123/648d2345FO56P5674/8890123OP6789019.jpg', 90, 8),
('小龟粮', '营养均衡，促进小龟成长', 22.90, 'https://img10.360buyimg.com/n1/jfs/t1/18456/24/26123/28890/647e3456FP67Q6785/9901234PQ7890120.jpg', 150, 8),
('龟缸', '半水半陆，适合龟类生活', 159.00, 'https://img11.360buyimg.com/n1/jfs/t1/20789/37/24567/35123/645f4567FQ78R8908/0012345QR8901231.jpg', 40, 8),
('水族箱', '桌面迷你，适合小型鱼类', 199.00, 'https://img14.360buyimg.com/n1/jfs/t1/17345/22/27123/32098/646a5678FR89S0121/1123456RS9012342.jpg', 30, 8);
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



-- 测试地址数据
INSERT INTO addresses (user_id, name, phone, province, city, district, detail_address, is_default) VALUES
(2, '张三', '13800138000', '北京市', '北京市', '朝阳区', '建国路 88 号 SOHO 现代城', true),
(2, '李四', '13900139000', '上海市', '上海市', '浦东新区', '世纪大道 100 号环球金融中心', false);



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


-- ==================== 收藏 / 优惠券 / 消息通知 ====================

-- 商品收藏表
CREATE TABLE IF NOT EXISTS product_favorites (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_product (user_id, product_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏表';

-- 优惠券模板表
CREATE TABLE IF NOT EXISTS coupons (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL COMMENT 'FULL_REDUCE 满减 / DISCOUNT 折扣',
    amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '满减金额',
    discount DECIMAL(3,2) DEFAULT 1.00 COMMENT '折扣率，0.80 表示 8 折',
    min_amount DECIMAL(10,2) DEFAULT 0.00 COMMENT '使用门槛金额',
    total INT DEFAULT 0 COMMENT '发放总量，0 表示不限量',
    received INT DEFAULT 0 COMMENT '已领取数量',
    start_time DATETIME,
    end_time DATETIME,
    status TINYINT DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

-- 用户优惠券表
CREATE TABLE IF NOT EXISTS user_coupons (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    coupon_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'UNUSED' COMMENT 'UNUSED 未使用 / USED 已使用 / EXPIRED 已过期',
    order_id INT COMMENT '使用的订单ID',
    received_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    used_at DATETIME,
    UNIQUE KEY uk_user_coupon (user_id, coupon_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

-- 消息通知表
CREATE TABLE IF NOT EXISTS notifications (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type VARCHAR(30) NOT NULL COMMENT 'ORDER 订单 / COUPON 优惠券 / SYSTEM 系统',
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500),
    related_id INT COMMENT '关联业务ID，如订单ID',
    is_read TINYINT DEFAULT 0 COMMENT '0 未读 / 1 已读',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_read (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内消息通知表';

-- ==================== 社区功能 ====================

-- 社区动态表
CREATE TABLE IF NOT EXISTS community_posts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    image_count TINYINT DEFAULT 0 COMMENT '配图数量，最多 3 张',
    like_count INT DEFAULT 0,
    comment_count INT DEFAULT 0,
    status TINYINT DEFAULT 1 COMMENT '1 正常 / 0 隐藏',
    visibility TINYINT DEFAULT 0 COMMENT '0 公开 / 1 仅自己可见',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_created (created_at),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区动态';

-- 社区动态配图（二进制存库，与商品图/头像一致）
CREATE TABLE IF NOT EXISTS community_post_images (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    sort_order TINYINT DEFAULT 0 COMMENT '展示顺序，从 0 开始',
    content_type VARCHAR(50) DEFAULT 'image/jpeg',
    image_data LONGBLOB,
    INDEX idx_post (post_id, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区动态配图';

-- 社区动态点赞表
CREATE TABLE IF NOT EXISTS community_post_likes (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_user (post_id, user_id),
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区动态点赞';

-- 社区动态评论表
CREATE TABLE IF NOT EXISTS community_post_comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_post (post_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区动态评论';

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

-- ==================== 我的宠物 ====================

-- 宠物档案表（头像二进制存库，与商品图/用户头像一致）
CREATE TABLE IF NOT EXISTS pets (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    name VARCHAR(20) NOT NULL COMMENT '昵称',
    species VARCHAR(20) DEFAULT 'cat' COMMENT 'cat 猫 / dog 狗 / other 其他',
    breed VARCHAR(30) COMMENT '具体品种，如狸花猫、比格犬',
    gender VARCHAR(10) DEFAULT 'unknown' COMMENT 'male 公 / female 母 / unknown 未知',
    birthday DATE NULL,
    weight DECIMAL(5,2) NULL COMMENT '体重，单位kg',
    note VARCHAR(100),
    avatar_data LONGBLOB,
    avatar_type VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='我的宠物';
