# 初始化 PetShop 数据库
Write-Host "正在初始化 PetShop 数据库..." -ForegroundColor Cyan
Write-Host ""

$MYSQL_PATH = "D:\mysql-8.0.45-winx64\bin\mysql.exe"
$SQL_FILE = "src\main\resources\db\init.sql"
$DB_USER = "root"
$DB_PASSWORD = "12345678"

Write-Host "请输入MySQL root密码 (默认为12345678)" -ForegroundColor Yellow
Write-Host ""

# 检查MySQL是否存在
if (-not (Test-Path $MYSQL_PATH)) {
    Write-Host "错误：找不到MySQL！路径: $MYSQL_PATH" -ForegroundColor Red
    Write-Host "请检查MySQL安装路径是否正确" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

# 检查SQL文件是否存在
if (-not (Test-Path $SQL_FILE)) {
    Write-Host "错误：找不到SQL文件！路径: $SQL_FILE" -ForegroundColor Red
    Read-Host "按回车键退出"
    exit 1
}

# 执行SQL脚本
& $MYSQL_PATH -u $DB_USER -p < $SQL_FILE

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Green
    Write-Host "数据库初始化成功！" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Red
    Write-Host "数据库初始化失败！" -ForegroundColor Red
    Write-Host "请检查MySQL路径和密码是否正确" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
}

Write-Host ""
Read-Host "按回车键退出"
