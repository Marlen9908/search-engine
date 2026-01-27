#!/bin/bash

echo "=== Поисковый движок ==="
echo ""

# Проверка Java
if ! command -v java &> /dev/null; then
    echo "Ошибка: Java не установлена"
    echo "Установите JDK 17 или выше"
    exit 1
fi

echo "Java версия:"
java -version
echo ""

# Проверка PostgreSQL
if ! command -v psql &> /dev/null; then
    echo "Предупреждение: PostgreSQL не найден в PATH"
    echo "Убедитесь, что PostgreSQL установлен и запущен"
fi

# Сборка проекта
echo "Сборка проекта..."
mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo ""
    echo "Сборка завершена успешно!"
    echo ""
    echo "Запуск приложения..."
    echo "Приложение будет доступно по адресу: http://localhost:8080"
    echo ""
    java -jar target/search-engine-1.0.0.jar
else
    echo ""
    echo "Ошибка сборки проекта"
    exit 1
fi
