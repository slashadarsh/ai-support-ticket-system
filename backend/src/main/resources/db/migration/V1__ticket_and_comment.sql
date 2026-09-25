CREATE SEQUENCE ticket_key_seq START WITH 1001;

CREATE TABLE ticket (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_key       VARCHAR(20)   NOT NULL,
    title            VARCHAR(200)  NOT NULL,
    description      VARCHAR(5000) NOT NULL,
    priority         VARCHAR(10)   NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    category         VARCHAR(20)   NOT NULL CHECK (category IN ('PAYMENT', 'SHIPPING', 'ACCOUNT', 'TECHNICAL', 'OTHER')),
    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN'
                     CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    assignee         VARCHAR(100),
    resolution_notes VARCHAR(5000),
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ux_ticket_key UNIQUE (ticket_key)
);

CREATE INDEX ix_ticket_status ON ticket (status);
CREATE INDEX ix_ticket_created_at ON ticket (created_at DESC);

CREATE TABLE comment (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ticket_id  BIGINT        NOT NULL REFERENCES ticket (id) ON DELETE CASCADE,
    author     VARCHAR(100)  NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_comment_ticket ON comment (ticket_id, created_at);
