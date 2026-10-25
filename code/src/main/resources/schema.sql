CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    login VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role ENUM('READER', 'LIBRARIAN', 'ADMIN') NOT NULL,
    status ENUM('ACTIVE', 'DEACTIVATED') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at TIMESTAMP NULL
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP NULL;
UPDATE users SET deactivated_at = CURRENT_TIMESTAMP
WHERE status = 'DEACTIVATED' AND deactivated_at IS NULL;

CREATE TABLE IF NOT EXISTS readers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    card_number VARCHAR(40) NOT NULL UNIQUE,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(160),
    phone VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deactivated_at TIMESTAMP NULL,
    CONSTRAINT fk_readers_user FOREIGN KEY (user_id) REFERENCES users(id)
);

ALTER TABLE readers ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP NULL;
UPDATE readers SET deactivated_at = CURRENT_TIMESTAMP
WHERE active = FALSE AND deactivated_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_readers_email ON readers (email);
CREATE UNIQUE INDEX IF NOT EXISTS uq_readers_phone ON readers (phone);

CREATE TABLE IF NOT EXISTS books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(240) NOT NULL,
    author VARCHAR(180) NOT NULL,
    genre VARCHAR(100),
    isbn VARCHAR(20),
    publication_year SMALLINT,
    publisher VARCHAR(180),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at TIMESTAMP NULL,
    INDEX idx_books_title (title),
    INDEX idx_books_author (author)
);

ALTER TABLE books ADD COLUMN IF NOT EXISTS publication_year SMALLINT;
ALTER TABLE books ADD COLUMN IF NOT EXISTS publisher VARCHAR(180);
ALTER TABLE books ADD COLUMN IF NOT EXISTS deactivated_at TIMESTAMP NULL;
UPDATE books SET deactivated_at = CURRENT_TIMESTAMP
WHERE active = FALSE AND deactivated_at IS NULL;

CREATE TABLE IF NOT EXISTS book_copies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    book_id BIGINT NOT NULL,
    inventory_number VARCHAR(60) NOT NULL UNIQUE,
    status ENUM('AVAILABLE', 'ISSUED') NOT NULL DEFAULT 'AVAILABLE',
    CONSTRAINT fk_copies_book FOREIGN KEY (book_id) REFERENCES books(id)
);

CREATE TABLE IF NOT EXISTS loans (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    copy_id BIGINT NOT NULL,
    reader_id BIGINT NOT NULL,
    librarian_id BIGINT NOT NULL,
    issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date DATE NOT NULL,
    returned_at TIMESTAMP NULL,
    CONSTRAINT fk_loans_copy FOREIGN KEY (copy_id) REFERENCES book_copies(id),
    CONSTRAINT fk_loans_reader FOREIGN KEY (reader_id) REFERENCES readers(id),
    CONSTRAINT fk_loans_librarian FOREIGN KEY (librarian_id) REFERENCES users(id),
    INDEX idx_loans_reader_active (reader_id, returned_at),
    INDEX idx_loans_due_date (due_date, returned_at)
);