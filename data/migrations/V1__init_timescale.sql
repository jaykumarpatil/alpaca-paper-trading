CREATE EXTENSION IF NOT EXISTS timescaledb;
SELECT create_hypertable('order_events', by_range('event_time'), if_not_exists => TRUE);
SELECT create_hypertable('execution_reports', by_range('event_time'), if_not_exists => TRUE);
