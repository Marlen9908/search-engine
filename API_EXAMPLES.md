# Примеры API запросов

## 1. Получение статистики

```bash
curl http://localhost:8080/api/statistics
```

Ответ:
```json
{
  "result": true,
  "statistics": {
    "total": {
      "sites": 2,
      "pages": 150,
      "lemmas": 5000,
      "isIndexing": false
    },
    "detailed": [
      {
        "url": "http://www.playback.ru/",
        "name": "PlayBack.Ru",
        "status": "INDEXED",
        "statusTime": 1706356534000,
        "error": "",
        "pages": 75,
        "lemmas": 2500
      }
    ]
  }
}
```

## 2. Запуск индексации

```bash
curl http://localhost:8080/api/startIndexing
```

Ответ:
```json
{
  "result": true
}
```

## 3. Остановка индексации

```bash
curl http://localhost:8080/api/stopIndexing
```

Ответ:
```json
{
  "result": true
}
```

## 4. Индексация отдельной страницы

```bash
curl -X POST "http://localhost:8080/api/indexPage?url=http://www.playback.ru/about.html"
```

Ответ:
```json
{
  "result": true
}
```

Ошибка (страница не из списка сайтов):
```json
{
  "result": false,
  "error": "Данная страница находится за пределами сайтов, указанных в конфигурационном файле"
}
```

## 5. Поиск по всем сайтам

```bash
curl "http://localhost:8080/api/search?query=купить+гитару"
```

Ответ:
```json
{
  "result": true,
  "count": 5,
  "data": [
    {
      "site": "http://www.playback.ru/",
      "siteName": "PlayBack.Ru",
      "uri": "/guitars/acoustic",
      "title": "Акустические гитары",
      "snippet": "...где <b>купить</b> качественную <b>гитару</b>...",
      "relevance": 0.95
    }
  ]
}
```

## 6. Поиск по конкретному сайту

```bash
curl "http://localhost:8080/api/search?query=новости&site=http://www.playback.ru/"
```

## 7. Поиск с пагинацией

```bash
curl "http://localhost:8080/api/search?query=музыка&offset=20&limit=10"
```

Параметры:
- `query` - поисковый запрос (обязательный)
- `site` - URL сайта для поиска (необязательный)
- `offset` - смещение (по умолчанию 0)
- `limit` - количество результатов (по умолчанию 20)

## Коды ошибок

### Индексация уже запущена
```json
{
  "result": false,
  "error": "Индексация уже запущена"
}
```

### Индексация не запущена
```json
{
  "result": false,
  "error": "Индексация не запущена"
}
```

### Пустой поисковый запрос
```json
{
  "result": false,
  "error": "Задан пустой поисковый запрос"
}
```

## Примеры использования с JavaScript

```javascript
// Запуск индексации
fetch('/api/startIndexing')
  .then(response => response.json())
  .then(data => console.log(data));

// Поиск
fetch('/api/search?query=example')
  .then(response => response.json())
  .then(data => {
    console.log('Найдено:', data.count);
    data.data.forEach(item => {
      console.log(item.title, item.relevance);
    });
  });

// Индексация страницы
fetch('/api/indexPage?url=http://example.com/page', { method: 'POST' })
  .then(response => response.json())
  .then(data => console.log(data));
```

## Примеры использования с Python

```python
import requests

# Статистика
response = requests.get('http://localhost:8080/api/statistics')
print(response.json())

# Поиск
response = requests.get('http://localhost:8080/api/search', 
                       params={'query': 'example'})
results = response.json()
print(f"Найдено: {results['count']}")

# Индексация страницы
response = requests.post('http://localhost:8080/api/indexPage',
                        params={'url': 'http://example.com/page'})
print(response.json())
```
