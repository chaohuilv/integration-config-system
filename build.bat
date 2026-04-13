@echo off
chcp 65001 >nul
echo ==========================================
echo  统一接口配置系统 - 编译脚本
echo ==========================================
echo.

REM 检查 Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 Java，请安装 JDK 17+
    pause
    exit /b 1
)

echo [1/3] 检查 Java 版本...
java -version 2>&1 | findstr "version"
echo.

REM 创建目录结构
echo [2/3] 创建编译目录...
if not exist target\classes mkdir target\classes

REM 编译 Java 文件
echo [3/3] 编译源代码...
echo 这可能需要几分钟，请耐心等待...
echo.

REM 使用 IDEA 的编译输出（如果存在）
if exist out\production\integration-config-system (
    echo 发现 IDEA 编译输出，直接打包...
    xcopy /E /I /Y out\production\integration-config-system\* target\classes\ >nul 2>&1
    goto :package
)

echo 未找到 IDEA 编译输出，请使用以下方式编译：
echo.
echo   方式1：在 IDEA 中按 Ctrl+F9 编译
echo   方式2：在 IDEA 终端运行：mvn clean package
echo   方式3：安装 Maven 后运行：mvn clean package
echo.
pause
exit /b 1

:package
echo 编译完成！
pause
