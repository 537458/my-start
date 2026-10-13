@echo off
echo 正在初始化 PetShop 数据库...
echo.

set MYSQL_PATH=D:\mysql-8.0.45-winx64\bin\mysql
set SQL_FILE=src\main\resources\db\init.sql
set DB_USER=root
set DB_PASSWORD=12345678

echo 请输入MySQL root密码 (默认为12345678)
echo.

"%MYSQL_PATH%" -u %DB_USER% -p < "%SQL_FILE%"

if %errorlevel% equ 0 (
    echo.
    echo ========================================
    echo 数据库初始化成功！
    echo ========================================
) else (
    echo.
    echo ========================================
    echo 数据库初始化失败！
    echo 请检查MySQL路径和密码是否正确
    echo ========================================
)

pause
