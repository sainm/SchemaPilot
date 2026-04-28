CREATE TABLE accounts (
  id NUMBER(10) PRIMARY KEY,
  email VARCHAR2(200) UNIQUE,
  balance NUMBER(12,2) CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_email ON accounts(email);

CREATE SEQUENCE account_seq START WITH 1 INCREMENT BY 1;

CREATE VIEW active_accounts AS
  SELECT id, NVL(email, 'unknown') AS email_text FROM accounts WHERE ROWNUM <= 10;
