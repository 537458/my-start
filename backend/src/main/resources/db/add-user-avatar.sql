-- 为users表添加头像相关字段
-- 注意：如果字段已存在，会报错但不影响其他操作

-- 添加avatar_data字段（二进制头像数据）
ALTER TABLE users ADD COLUMN avatar_data LONGBLOB;

-- 添加avatar_type字段（头像MIME类型）
ALTER TABLE users ADD COLUMN avatar_type VARCHAR(50);

-- 更新现有用户的默认头像（使用默认头像URL）
UPDATE users SET avatar = 'default_avatar' WHERE avatar IS NULL OR avatar = '';
