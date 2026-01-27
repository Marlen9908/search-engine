# Установка и настройка PostgreSQL

## Windows

### 1. Скачивание

Скачайте установщик PostgreSQL с официального сайта:
https://www.postgresql.org/download/windows/

### 2. Установка

1. Запустите установщик
2. Выберите компоненты: PostgreSQL Server, pgAdmin 4, Command Line Tools
3. Укажите директорию установки (по умолчанию: C:\Program Files\PostgreSQL\15)
4. Укажите директорию для данных (по умолчанию: C:\Program Files\PostgreSQL\15\data)
5. Установите пароль для пользователя postgres (запомните его!)
6. Порт: 5432 (по умолчанию)
7. Локаль: Russian, Russia или Default locale
8. Дождитесь окончания установки

### 3. Создание базы данных

#### Вариант 1: Через pgAdmin 4

1. Откройте pgAdmin 4
2. Подключитесь к серверу (введите пароль postgres)
3. Правый клик на "Databases" → Create → Database
4. Имя: `search_engine`
5. Owner: postgres
6. Encoding: UTF8
7. Нажмите Save

#### Вариант 2: Через командную строку

```cmd
cd "C:\Program Files\PostgreSQL\15\bin"
psql -U postgres
```

Введите пароль, затем выполните:

```sql
CREATE DATABASE search_engine;
CREATE USER search_user WITH PASSWORD 'search_password';
GRANT ALL PRIVILEGES ON DATABASE search_engine TO search_user;
\q
```

### 4. Настройка приложения

В файле `application.yaml` укажите:

```yaml
spring:
  datasource:
    username: postgres
    password: ваш_пароль
    url: jdbc:postgresql://localhost:5432/search_engine
```

---

## Linux (Ubuntu/Debian)

### 1. Установка

```bash
sudo apt update
sudo apt install postgresql postgresql-contrib
```

### 2. Запуск сервиса

```bash
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

### 3. Создание базы данных

```bash
sudo -u postgres psql
```

В консоли PostgreSQL:

```sql
CREATE DATABASE search_engine;
CREATE USER search_user WITH PASSWORD 'search_password';
GRANT ALL PRIVILEGES ON DATABASE search_engine TO search_user;

-- Подключение к БД
\c search_engine

-- Выдача прав
GRANT ALL ON SCHEMA public TO search_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO search_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO search_user;

-- Выход
\q
```

### 4. Настройка доступа (опционально)

Отредактируйте `/etc/postgresql/15/main/pg_hba.conf`:

```
# IPv4 local connections:
host    all             all             127.0.0.1/32            md5
```

Перезапустите PostgreSQL:

```bash
sudo systemctl restart postgresql
```

---

## macOS

### 1. Установка через Homebrew

```bash
brew install postgresql@15
```

### 2. Запуск

```bash
brew services start postgresql@15
```

### 3. Создание базы данных

```bash
psql postgres
```

Затем выполните те же SQL команды, что и для Linux.

---

## Проверка подключения

### Через psql

```bash
psql -U search_user -d search_engine -h localhost
```

Введите пароль `search_password`. Если подключение успешно, вы увидите приглашение:

```
search_engine=>
```

### Через приложение

Запустите Spring Boot приложение и проверьте логи. Должна появиться строка:

```
HHH000204: Processing PersistenceUnitInfo [name: default]
```

Без ошибок подключения.

---

## Полезные команды PostgreSQL

```sql
-- Список баз данных
\l

-- Подключение к БД
\c search_engine

-- Список таблиц
\dt

-- Описание таблицы
\d site

-- Выполнение SQL запроса
SELECT * FROM site;

-- Выход
\q
```

---

## Решение проблем

### Ошибка: "password authentication failed"

1. Проверьте правильность пароля в `application.yaml`
2. Убедитесь, что пользователь создан:

```sql
SELECT usename FROM pg_user WHERE usename = 'search_user';
```

### Ошибка: "database does not exist"

Создайте базу данных:

```sql
CREATE DATABASE search_engine;
```

### Ошибка: "Connection refused"

1. Проверьте, запущен ли PostgreSQL:

**Windows:**
```cmd
services.msc → найдите postgresql-x64-15
```

**Linux:**
```bash
sudo systemctl status postgresql
```

2. Проверьте порт в `application.yaml` (должен быть 5432)

### Ошибка: "permission denied"

Выдайте права пользователю:

```sql
GRANT ALL PRIVILEGES ON DATABASE search_engine TO search_user;
GRANT ALL ON SCHEMA public TO search_user;
```

---

## Бэкап и восстановление

### Создание бэкапа

```bash
pg_dump -U search_user search_engine > backup.sql
```

### Восстановление

```bash
psql -U search_user search_engine < backup.sql
```

---

## GUI инструменты

- **pgAdmin 4** - устанавливается вместе с PostgreSQL
- **DBeaver** - https://dbeaver.io/
- **DataGrip** - https://www.jetbrains.com/datagrip/ (платный)

---

## Конфигурация для production

В `application.yaml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: validate
```
