-- BankFlow database bootstrap script.
-- This file is mounted into MySQL so all required schemas are created automatically
-- the first time the mysql-data volume is initialized.

-- Used by auth-service for users, roles, credentials, refresh tokens, OTPs, and security audit metadata.
CREATE DATABASE IF NOT EXISTS bankflow_auth;

-- Used by account-service for customer accounts, balances, ledger metadata, and account-level audit trails.
CREATE DATABASE IF NOT EXISTS bankflow_account;

-- Used by payment-service for payment requests, transfers, transaction state, settlement, and idempotency metadata.
CREATE DATABASE IF NOT EXISTS bankflow_payment;

-- Used by notification-service for email/SMS notification history, templates, delivery attempts, and outbox data.
CREATE DATABASE IF NOT EXISTS bankflow_notification;
