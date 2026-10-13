-- 数据库字段删除前的备份脚本
-- 执行时间: 2026-05-15

-- 备份users表
CREATE TABLE IF NOT EXISTS users_backup_20260515 AS SELECT * FROM users;

-- 备份products表
CREATE TABLE IF NOT EXISTS products_backup_20260515 AS SELECT * FROM products;

-- 备份categories表
CREATE TABLE IF NOT EXISTS categories_backup_20260515 AS SELECT * FROM categories;

-- 备份orders表
CREATE TABLE IF NOT EXISTS orders_backup_20260515 AS SELECT * FROM orders;

-- 备份shopping_carts表
CREATE TABLE IF NOT EXISTS shopping_carts_backup_20260515 AS SELECT * FROM shopping_carts;

-- 备份order_items表
CREATE TABLE IF NOT EXISTS order_items_backup_20260515 AS SELECT * FROM order_items;

-- 备份addresses表
CREATE TABLE IF NOT EXISTS addresses_backup_20260515 AS SELECT * FROM addresses;

-- 回滚脚本（如果需要恢复）
-- DROP TABLE users;
-- RENAME TABLE users_backup_20260515 TO users;
-- DROP TABLE products;
-- RENAME TABLE products_backup_20260515 TO products;
-- DROP TABLE categories;
-- RENAME TABLE categories_backup_20260515 TO categories;
-- DROP TABLE orders;
-- RENAME TABLE orders_backup_20260515 TO orders;
-- DROP TABLE shopping_carts;
-- RENAME TABLE shopping_carts_backup_20260515 TO shopping_carts;
-- DROP TABLE order_items;
-- RENAME TABLE order_items_backup_20260515 TO order_items;
-- DROP TABLE addresses;
-- RENAME TABLE addresses_backup_20260515 TO addresses;
