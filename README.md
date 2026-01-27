# Поисковый движок

Локальный поисковый движок для индексации и поиска по веб-сайтам.

## Описание

Это Spring Boot приложение, которое:
- Обходит веб-страницы сайтов и индексирует их содержимое
- Выполняет морфологический анализ текста (лемматизацию)
- Предоставляет API для поиска по проиндексированным страницам
- Ранжирует результаты поиска по релевантности

## Стек технологий

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- PostgreSQL
- Jsoup (парсинг HTML)
- Apache Lucene Morphology (лемматизация)
- Maven
- Thymeleaf (веб-интерфейс)
- Lombok

## Требования

- JDK 17 или выше
- PostgreSQL 12 или выше
- Maven 3.6+

## Установка и запуск

### 1. Установка PostgreSQL

Установите PostgreSQL и создайте базу данных:

```sql
CREATE DATABASE search_engine;
CREATE USER search_user WITH PASSWORD 'search_password';
GRANT ALL PRIVILEGES ON DATABASE search_engine TO search_user;
```

### 2. Настройка приложения

Отредактируйте файл `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    username: search_user
    password: search_password
    url: jdbc:postgresql://localhost:5432/search_engine
```

Настройте список сайтов для индексации:

```yaml
indexing-settings:
  sites:
    - url: http://www.playback.ru/
      name: PlayBack.Ru
    - url: https://volochek.life/
      name: Volochek Life
```

### 3. Настройка Maven для библиотек лемматизации

Создайте или отредактируйте файл `~/.m2/settings.xml`:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
          https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>skillbox-gitlab</id>
            <configuration>
                <httpHeaders>
                    <property>
                        <name>Private-Token</name>
                        <value>wtb5axJDFX9Vm_W1Lexg</value>
                    </property>
                </httpHeaders>
            </configuration>
        </server>
    </servers>
</settings>
```

### 4. Сборка проекта

```bash
mvn clean install
```

### 5. Запуск приложения

```bash
mvn spring-boot:run
```

или

```bash
java -jar target/search-engine-1.0.0.jar
```

Приложение будет доступно по адресу: http://localhost:8080

## API

### Статистика

```
GET /api/statistics
```

Возвращает информацию о проиндексированных сайтах, страницах и леммах.

### Запуск индексации

```
GET /api/startIndexing
```

Запускает полную индексацию всех сайтов из конфигурации.

### Остановка индексации

```
GET /api/stopIndexing
```

Останавливает текущий процесс индексации.

### Индексация отдельной страницы

```
POST /api/indexPage?url=<URL>
```

Индексирует или переиндексирует отдельную страницу.

### Поиск

```
GET /api/search?query=<запрос>&site=<URL сайта>&offset=0&limit=20
```

Параметры:
- `query` (обязательный) - поисковый запрос
- `site` (необязательный) - URL сайта для поиска (если не указан, поиск по всем сайтам)
- `offset` - смещение для пагинации (по умолчанию 0)
- `limit` - количество результатов (по умолчанию 20)

## Структура базы данных

### Таблица `site`
- `id` - ID сайта
- `status` - статус индексации (INDEXING, INDEXED, FAILED)
- `status_time` - время статуса
- `last_error` - текст последней ошибки
- `url` - URL сайта
- `name` - название сайта

### Таблица `page`
- `id` - ID страницы
- `site_id` - ID сайта
- `path` - путь к странице
- `code` - HTTP код ответа
- `content` - содержимое страницы

### Таблица `lemma`
- `id` - ID леммы
- `site_id` - ID сайта
- `lemma` - нормальная форма слова
- `frequency` - количество страниц, на которых встречается

### Таблица `search_index`
- `id` - ID записи
- `page_id` - ID страницы
- `lemma_id` - ID леммы
- `lemma_rank` - количество данной леммы на странице

## Принцип работы

1. **Индексация**: Приложение обходит все страницы сайта с помощью многопоточного алгоритма (Fork/Join Pool)
2. **Лемматизация**: Текст каждой страницы разбивается на слова, которые приводятся к нормальной форме
3. **Сохранение**: Леммы и их количество сохраняются в базу данных
4. **Поиск**: По поисковому запросу находятся страницы, содержащие все леммы запроса
5. **Ранжирование**: Страницы сортируются по релевантности (частоте встречаемости лемм)

## Веб-интерфейс

Приложение имеет простой веб-интерфейс для:
- Управления индексацией
- Выполнения поиска
- Просмотра статистики

## Разработка

### Структура проекта

```
src/
├── main/
│   ├── java/searchengine/
│   │   ├── config/          # Конфигурация
│   │   ├── controllers/     # REST контроллеры
│   │   ├── dto/            # Объекты передачи данных
│   │   ├── model/          # Entity модели
│   │   ├── repositories/   # JPA репозитории
│   │   ├── services/       # Бизнес-логика
│   │   └── Application.java
│   └── resources/
│       ├── application.yaml
│       └── templates/
└── test/
```

## Лицензия

Проект создан в образовательных целях.

## Автор

Разработано в рамках итогового проекта курса Java-разработчик.
