@echo off
echo === Поисковый движок ===
echo.

REM Проверка Java
java -version >nul 2>&1
if errorlevel 1 (
    echo Ошибка: Java не установлена
    echo Установите JDK 17 или выше
    pause
    exit /b 1
)

echo Java установлена
java -version
echo.

REM Сборка проекта
echo Сборка проекта...
call mvn clean package -DskipTests

if errorlevel 1 (
    echo.
    echo Ошибка сборки проекта
    pause
    exit /b 1
)

echo.
echo Сборка завершена успешно!
echo.
echo Запуск приложения...
echo Приложение будет доступно по адресу: http://localhost:8080
echo.

java -jar target\search-engine-1.0.0.jar

pause
