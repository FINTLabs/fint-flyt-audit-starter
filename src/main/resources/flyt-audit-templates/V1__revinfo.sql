CREATE SEQUENCE IF NOT EXISTS revinfo_seq
    INCREMENT BY 50
    START WITH 1;

CREATE TABLE IF NOT EXISTS revinfo (
    rev      BIGINT NOT NULL DEFAULT nextval('revinfo_seq'),
    revtstmp BIGINT NOT NULL,
    actor    JSONB  NOT NULL DEFAULT '{"type":"UNKNOWN"}'::jsonb,
    CONSTRAINT pk_revinfo PRIMARY KEY (rev)
);
