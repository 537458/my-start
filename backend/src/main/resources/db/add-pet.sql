-- 我的宠物：给已有库补建 pets 表（可重复执行）
-- 用法：mysql -u root -p petshop < add-pet.sql

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
