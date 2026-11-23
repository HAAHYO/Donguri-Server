CREATE TABLE employee (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    department VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    username VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_employee_username UNIQUE (username),
    CONSTRAINT chk_employee_role CHECK (role IN ('FDS_ADMIN', 'CRM_MARKETER'))
);

CREATE TABLE customer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    app_login_freq INT NOT NULL,
    avg_tx_amount DECIMAL(19, 2) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL,
    churn_score DECIMAL(5, 4) NOT NULL,
    grade VARCHAR(20) NOT NULL,
    is_false_positive_victim BIT(1) NOT NULL,
    name VARCHAR(50) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customer_account_number UNIQUE (account_number),
    CONSTRAINT chk_customer_grade CHECK (grade IN ('VIP', 'GOLD', 'GENERAL'))
);

CREATE TABLE transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    device_changed BIT(1) NOT NULL,
    fds_status VARCHAR(20) NOT NULL,
    fraud_score DECIMAL(5, 4) NOT NULL,
    is_actual_fraud BIT(1) NULL,
    is_overseas BIT(1) NOT NULL,
    occurred_at DATETIME(6) NULL,
    shap_reasons LONGTEXT NOT NULL,
    tx_hour INT NOT NULL,
    customer_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transactions_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT chk_transactions_fds_status CHECK (fds_status IN ('NORMAL', 'WARNING', 'BLOCKED'))
);

CREATE TABLE care_action (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    action_message VARCHAR(500) NOT NULL,
    recommended_product VARCHAR(100) NOT NULL,
    sent_by VARCHAR(50) NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    transaction_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_care_action_transaction UNIQUE (transaction_id),
    CONSTRAINT fk_care_action_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_care_action_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    CONSTRAINT chk_care_action_status CHECK (status IN ('PENDING', 'SENT'))
);

CREATE TABLE care_action_send_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    action_message VARCHAR(500) NOT NULL,
    care_action_version BIGINT NOT NULL,
    recommended_product VARCHAR(100) NOT NULL,
    sent_at DATETIME(6) NOT NULL,
    sent_by VARCHAR(50) NOT NULL,
    care_action_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_care_action_send_history_care_action FOREIGN KEY (care_action_id) REFERENCES care_action (id)
);
