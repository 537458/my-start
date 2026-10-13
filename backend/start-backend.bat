@echo off
chcp 65001 >nul
echo ========================================
echo    宠物商城后端服务启动脚本
echo ========================================
echo.

REM 检查 Java 环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Java 环境，请先安装 JDK 17+
    pause
    exit /b 1
)

echo [信息] 正在检查 Maven...
mvn -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到 Maven，请先安装 Maven 3.6+
    pause
    exit /b 1
)

echo.
echo [信息] 正在启动后端服务...
echo [提示] 启动完成后会看到 "Started PetShopApplication" 提示
echo.

cd /d "%~dp0"
call mvn spring-boot:run

pause
