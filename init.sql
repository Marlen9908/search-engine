-- Создание базы данных
CREATE DATABASE search_engine
    WITH 
    ENCODING = 'UTF8'
    LC_COLLATE = 'ru_RU.UTF-8'
    LC_CTYPE = 'ru_RU.UTF-8'
    TEMPLATE = template0;

-- Создание пользователя
CREATE USER search_user WITH PASSWORD 'search_password';

-- Выдача прав
GRANT ALL PRIVILEGES ON DATABASE search_engine TO search_user;

-- Подключение к БД
\c search_engine

-- Выдача прав на схему public
GRANT ALL ON SCHEMA public TO search_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO search_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO search_user;

-- Установка прав по умолчанию
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO search_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO search_user;
