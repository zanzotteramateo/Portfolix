-- Cambio de mail: el token guarda el mail nuevo, que se aplica recién cuando se confirma el link.
ALTER TABLE email_tokens ADD COLUMN new_email VARCHAR(254);

ALTER TABLE email_tokens DROP CONSTRAINT ck_email_tokens_purpose;
ALTER TABLE email_tokens ADD CONSTRAINT ck_email_tokens_purpose
    CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'EMAIL_CHANGE'));

-- El mail nuevo está si y solo si el token es de un cambio de mail.
ALTER TABLE email_tokens ADD CONSTRAINT ck_email_tokens_new_email
    CHECK ((purpose = 'EMAIL_CHANGE') = (new_email IS NOT NULL));
