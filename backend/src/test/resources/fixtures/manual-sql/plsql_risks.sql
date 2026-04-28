CREATE OR REPLACE TRIGGER trg_accounts_bi
BEFORE INSERT ON accounts
FOR EACH ROW
BEGIN
  :NEW.created_at := SYSDATE;
END;
/

CREATE OR REPLACE FUNCTION normalize_email(p_email VARCHAR2)
RETURN VARCHAR2
AS
BEGIN
  RETURN NVL(p_email, '');
END;
/

CREATE OR REPLACE PACKAGE pkg_accounts AS
  g_counter NUMBER := 0;
  PROCEDURE sync_accounts;
END pkg_accounts;
/
