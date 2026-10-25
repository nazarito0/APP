# Інформаційна система обліку бібліотеки

Вебзастосунок для лабораторної роботи №3. Сервер: Java 17, Spring Boot, JDBC. База даних: MariaDB. Інтерфейс: HTML, CSS і JavaScript.

## Запуск

Потрібні JDK 17+, Maven 3.9+ і MariaDB 10.6+.

Створіть базу даних і користувача MariaDB:

```sql
CREATE DATABASE library_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'library_user'@'localhost' IDENTIFIED BY 'library_pass';
GRANT ALL PRIVILEGES ON library_db.* TO 'library_user'@'localhost';
```

З каталогу `code` виконайте:

```powershell
mvn spring-boot:run
```

Після запуску відкрийте <http://localhost:8080>. Таблиці створюються автоматично зі `src/main/resources/schema.sql`.

### Локальний HTTPS

Створіть самопідписаний сертифікат для локальної перевірки (у PowerShell з каталогу `code`):

```powershell
New-Item -ItemType Directory -Force certs
keytool -genkeypair -alias library-local -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore certs/library-local.p12 -validity 365 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"
```

`keytool` попросить пароль сховища; задайте власний. Потім у тому ж PowerShell задайте його для Spring і запустіть HTTPS-профіль:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'https'
$env:TLS_KEYSTORE = 'file:./certs/library-local.p12'
$env:TLS_KEYSTORE_PASSWORD = 'YOUR_KEYSTORE_PASSWORD'
mvn spring-boot:run
```

Відкрийте <https://localhost:8443>. Браузер попереджатиме про самопідписаний сертифікат; не використовуйте його для публічного розгортання. Для production потрібен сертифікат від довіреного CA або TLS на reverse proxy. Файли `certs/` і приватні ключі ігноруються Git.

Початковий адміністратор: `admin` / `admin123`. Для власного середовища задайте `ADMIN_LOGIN` і `ADMIN_PASSWORD` до першого запуску.

Підключення до БД можна перевизначити змінними `DB_URL`, `DB_USERNAME` і `DB_PASSWORD`. Для HTTPS-середовища встановіть `SESSION_COOKIE_SECURE=true`.

## Реалізовано

- Публічний каталог із пошуком за назвою, автором та ISBN.
- Реєстрація читацького формуляра й вхід із BCrypt-хешуванням паролів.
- Зміна власного пароля з перевіркою поточного та адміністративне скидання пароля будь-якого акаунта.
- Розмежування ролей читача, бібліотекаря й адміністратора.
- Додавання й редагування видань із роком, видавництвом та фізичними примірниками; пошук від 3 символів без урахування регістру.
- Реєстрація читача бібліотекарем, редагування контактів та деактивація формуляра лише за відсутності активних видач.
- Деактивація видання з блокуванням активних видач; усі такі операції є м'яким видаленням.
- Видача й повернення в транзакціях; примірник блокується через `SELECT ... FOR UPDATE`, щоб запобігти подвійній видачі.
- Історія видач і визначення прострочених повернень; строк видачі — 14 днів.
- Звіти для адміністратора по фонду, читачах і видачах; створення та керування статусом облікових записів працівників.
- Щоденна автоматична деактивація облікових записів після 30 днів без входу.
- Часові мітки деактивації; очищення неактивних книг/читачів/акаунтів не раніше ніж через 30 днів, історія повернених видач зберігається щонайменше 3 роки.

## API

- `POST /api/auth/login`, `POST /api/auth/register`, `GET /api/auth/me`, `POST /api/auth/logout`, `PUT /api/auth/password`
- `GET /api/books?search=...`, `GET /api/books/{id}`, `POST /api/books`, `PUT /api/books/{id}`, `DELETE /api/books/{id}`
- `GET /api/readers`, `POST /api/readers`, `PUT /api/readers/{id}`, `DELETE /api/readers/{id}`, `GET /api/readers/{id}/loans`
- `GET /api/copies/available`, `GET /api/loans`, `POST /api/loans`, `POST /api/loans/{id}/return`
- `GET /api/reports/summary`, `GET /api/reports?type=INVENTORY|READERS|LOANS`, `GET /api/users`, `GET /api/staff`, `POST /api/staff`, `PUT /api/users/{id}/status`, `PUT /api/users/{id}/password`
- `PUT /api/auth/password`

Пошук приймає не менше трьох символів. `DELETE` для книг і читачів є деактивацією; операцію буде відхилено, якщо є неповернуті видачі.

## Перевірка

```powershell
mvn test
```

## DAO та резервні копії

SQL-запити зосереджені в `src/main/java/ua/edu/tntu/library/repository`: `BookDAO`, `ReaderDAO`, `LoanDAO`, `UserDAO` і `ReportDAO`. Сервіси координують бізнес-правила й транзакції.

Щоденний backup запускається через `scripts/backup-mariadb.ps1`. Скрипт створює стиснений дамп `library_db`, включно з triggers/routines/events, у `%LOCALAPPDATA%\LibraryAccounting\backups` і видаляє архіви старші за 30 днів. Для ручного запуску спочатку створіть файл `%USERPROFILE%\.library-accounting-backup.cnf`:

```ini
[client]
user=library_user
password=YOUR_DATABASE_PASSWORD
host=127.0.0.1
port=3306
```

Замість `YOUR_DATABASE_PASSWORD` введіть пароль користувача БД локально. Не додавайте цей файл до репозиторію та обмежте доступ до нього своїм обліковим записом Windows:

```powershell
icacls "$env:USERPROFILE\.library-accounting-backup.cnf" /inheritance:r /grant:r "$($env:USERNAME):(R)"
.\scripts\backup-mariadb.ps1
```

Для щоденного запуску створіть задачу в **Планувальнику завдань Windows**: тригер **Щодня**, дія — `powershell.exe` з аргументами `-NoProfile -ExecutionPolicy Bypass -File "D:\Project_CIc-31\APP\code\scripts\backup-mariadb.ps1"`. Запускайте задачу від того самого Windows-користувача, якому доступний credentials-файл.

Або зареєструйте задачу автоматично після створення credentials-файлу:

```powershell
.\scripts\register-backup-task.ps1
```

За замовчуванням вона запускається щодня о 23:00, коли ваш Windows-користувач увійшов у систему. Щоб вибрати інший час, передайте параметр, наприклад `-At (Get-Date -Hour 2 -Minute 30)`.