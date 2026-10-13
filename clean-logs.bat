@echo off
echo 正在清理日志文件...
cd /d "%~dp0backend\logs"
del /q *.gz
echo 日志文件清理完成！
pause
