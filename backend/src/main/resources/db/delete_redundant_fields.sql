-- 删除冗余字段SQL脚本
-- 执行时间: 2026-05-16
-- 注意：MySQL 5.x不支持 DROP COLUMN IF EXISTS，所以直接执行DROP COLUMN
-- 如果字段不存在会报错，但不会影响其他字段的删除

-- ========================================
-- 删除categories表的冗余字段
-- ========================================
ALTER TABLE categories DROP COLUMN parent_id;
ALTER TABLE categories DROP COLUMN description;

-- ========================================
-- 删除orders表的冗余字段
-- ========================================
ALTER TABLE orders DROP COLUMN IF EXISTS create_time;
ALTER TABLE orders DROP COLUMN IF EXISTS consignee;
ALTER TABLE orders DROP COLUMN IF EXISTS product_id;
ALTER TABLE orders DROP COLUMN IF EXISTS price;
ALTER TABLE orders DROP COLUMN IF EXISTS quantity;
ALTER TABLE orders DROP COLUMN IF EXISTS remark;
ALTER TABLE orders DROP COLUMN IF EXISTS shipping_status;
ALTER TABLE orders DROP COLUMN IF EXISTS shipping_time;
ALTER TABLE orders DROP COLUMN IF EXISTS total_amount;
-- 注意：merchant_id 需要保留，用于关联商家
-- ALTER TABLE orders DROP COLUMN merchant_id;
ALTER TABLE orders DROP COLUMN IF EXISTS merchant_name;

-- ========================================
-- 删除users表的冗余字段
-- ========================================
ALTER TABLE users DROP COLUMN avatar;

-- ========================================
-- 删除products表的冗余字段
-- ========================================
ALTER TABLE products DROP COLUMN image_url;

-- ========================================
-- 验证删除结果
-- ========================================
SELECT 'users表结构:' AS '验证结果';
DESCRIBE users;

SELECT 'products表结构:' AS '验证结果';
DESCRIBE products;

SELECT 'categories表结构:' AS '验证结果';
DESCRIBE categories;

SELECT 'orders表结构:' AS '验证结果';
DESCRIBE orders;
