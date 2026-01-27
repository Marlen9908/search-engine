# FAQ - Часто задаваемые вопросы

## Общие вопросы

### Как работает поисковый движок?

1. **Индексация**: Приложение обходит все страницы сайта по ссылкам
2. **Лемматизация**: Текст разбивается на слова и приводится к базовой форме
3. **Хранение**: Леммы сохраняются в PostgreSQL
4. **Поиск**: По запросу ищутся страницы с нужными леммами
5. **Ранжирование**: Результаты сортируются по релевантности

### Сколько времени занимает индексация?

Зависит от размера сайта:
- Маленький сайт (50-100 страниц): 5-15 минут
- Средний сайт (1000 страниц): 30-60 минут
- Большой сайт (10000+ страниц): несколько часов

### Можно ли индексировать несколько сайтов одновременно?

Да, каждый сайт индексируется в отдельном потоке.

### Как часто нужно переиндексировать сайт?

Зависит от частоты обновления контента:
- Новостные сайты: каждый день
- Блоги: раз в неделю
- Статичные сайты: раз в месяц

## Технические вопросы

### Какой объем базы данных потребуется?

Примерная оценка:
- 1000 страниц ≈ 100 MB
- 10000 страниц ≈ 1 GB
- 100000 страниц ≈ 10 GB

### Сколько оперативной памяти нужно?

Минимум:
- JVM: 512 MB
- PostgreSQL: 256 MB
- Система: 512 MB
- **Итого: 1.5 GB**

Рекомендуется: 4 GB+

### Можно ли использовать MySQL вместо PostgreSQL?

Да, нужно изменить:

1. В `pom.xml`:
```xml
<dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-java</artifactId>
    <scope>runtime</scope>
</dependency>
```

2. В `application.yaml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/search_engine
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

### Как увеличить скорость индексации?

1. Увеличьте размер пула потоков в `SiteIndexer`:
```java
ForkJoinPool pool = new ForkJoinPool(16); // вместо дефолтного
```

2. Уменьшите задержку между запросами в `application.yaml`:
```yaml
indexing-settings:
  delay-ms: 500  # вместо 1000
```

⚠️ Внимание: слишком частые запросы могут привести к блокировке IP!

### Как настроить индексацию для защищённых сайтов?

Если сайт требует аутентификации, добавьте заголовки в `SiteIndexer`:

```java
Connection.Response response = Jsoup.connect(url)
    .userAgent(config.getUserAgent())
    .referrer(config.getReferrer())
    .cookie("auth", "token")
    .header("Authorization", "Bearer YOUR_TOKEN")
    .execute();
```

## Ошибки и их решение

### Ошибка: "401 Ошибка Авторизации" при загрузке библиотек

**Решение:**
1. Проверьте токен в `~/.m2/settings.xml`
2. Очистите кэш Maven: удалите `~/.m2/repository`
3. Обновите зависимости: `mvn clean install`

### Ошибка: "Connection refused" к PostgreSQL

**Решение:**
1. Убедитесь, что PostgreSQL запущен:
   - Windows: Службы → postgresql-x64-15
   - Linux: `sudo systemctl status postgresql`
2. Проверьте порт в `application.yaml` (5432)
3. Проверьте файрвол

### Ошибка: "OutOfMemoryError: Java heap space"

**Решение:**
Увеличьте память JVM при запуске:

```bash
java -Xmx2g -jar search-engine-1.0.0.jar
```

### Ошибка: "Too many connections" в PostgreSQL

**Решение:**
В `postgresql.conf`:

```
max_connections = 200
```

Перезапустите PostgreSQL.

### Индексация зависает

**Возможные причины:**
1. Сайт медленно отвечает → увеличьте `delay-ms`
2. Много страниц → это нормально, подождите
3. Циклические ссылки → проверьте логи

**Решение:**
Остановите индексацию и запустите заново.

### Поиск не находит страницы

**Проверьте:**
1. Завершена ли индексация (статус INDEXED)
2. Есть ли леммы в базе: `SELECT COUNT(*) FROM lemma;`
3. Правильный ли поисковый запрос (на русском языке)

### Результаты поиска нерелевантны

**Настройка:**
Измените `MAX_FREQUENCY_PERCENT` в `SearchServiceImpl`:

```java
private static final double MAX_FREQUENCY_PERCENT = 0.5; // вместо 0.8
```

Это исключит слишком частые слова.

## Оптимизация

### Как ускорить поиск?

1. Создайте индексы в PostgreSQL:
```sql
CREATE INDEX idx_lemma_frequency ON lemma(frequency);
CREATE INDEX idx_index_rank ON search_index(lemma_rank);
```

2. Используйте кэширование в Spring:
```java
@Cacheable("search")
public SearchResponse search(String query, ...) {
    // ...
}
```

### Как уменьшить размер базы данных?

1. Удалите старые данные:
```sql
DELETE FROM page WHERE code >= 400;
```

2. Очистите неиспользуемые леммы:
```sql
DELETE FROM lemma WHERE frequency = 0;
```

3. Выполните VACUUM:
```sql
VACUUM FULL;
```

### Как масштабировать приложение?

1. Используйте несколько экземпляров приложения
2. Настройте PostgreSQL репликацию
3. Добавьте балансировщик нагрузки (nginx)
4. Используйте Redis для кэширования

## Разработка

### Как добавить новый язык для лемматизации?

1. Добавьте зависимость в `pom.xml`:
```xml
<dependency>
    <groupId>org.apache.lucene.morphology</groupId>
    <artifactId>english</artifactId>
    <version>1.5</version>
</dependency>
```

2. Создайте лемматизатор в `LemmaFinder`:
```java
private final LuceneMorphology englishMorphology = new EnglishLuceneMorphology();
```

### Как изменить структуру базы данных?

1. Измените entity класс
2. В `application.yaml` установите:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```
3. Перезапустите приложение

Для production используйте миграции (Flyway/Liquibase).

### Как добавить логирование?

В `application.yaml`:

```yaml
logging:
  level:
    searchengine: DEBUG
    org.hibernate.SQL: DEBUG
  file:
    name: logs/search-engine.log
```

### Как запустить в Docker?

Создайте `Dockerfile`:

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/search-engine-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

Создайте `docker-compose.yml`:

```yaml
version: '3.8'
services:
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: search_engine
      POSTGRES_USER: search_user
      POSTGRES_PASSWORD: search_password
  
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - db
```

Запустите:
```bash
docker-compose up
```

## Безопасность

### Как защитить API?

Добавьте Spring Security:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

### Как ограничить частоту запросов?

Используйте Bucket4j для rate limiting.

### Как предотвратить SQL injection?

JPA автоматически защищает от SQL injection. Не используйте нативные SQL запросы.

## Поддержка

### Где получить помощь?

1. Проверьте документацию в README.md
2. Изучите примеры в API_EXAMPLES.md
3. Проверьте логи приложения
4. Обратитесь к куратору курса

### Как сообщить об ошибке?

Создайте issue на GitHub с:
- Описанием проблемы
- Шагами для воспроизведения
- Логами ошибки
- Версией Java и PostgreSQL
