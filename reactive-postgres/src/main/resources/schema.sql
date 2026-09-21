CREATE TABLE IF NOT EXISTS persons (
    id SERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    date_of_birth DATE,
    address VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(64) UNIQUE NOT NULL,
    holder_name VARCHAR(128) NOT NULL,
    email VARCHAR(128),
    account_type VARCHAR(32) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(64) UNIQUE NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    ticker VARCHAR(16) NOT NULL,
    side VARCHAR(16) NOT NULL,
    order_type VARCHAR(16) NOT NULL,
    price NUMERIC(19, 4),
    quantity NUMERIC(19, 4) NOT NULL,
    filled_quantity NUMERIC(19, 4) DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_orders_account ON orders(account_number);
CREATE INDEX IF NOT EXISTS idx_orders_ticker ON orders(ticker);

-- pgvector extension and dense embedding vector storage
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS market_research_reports (
    id VARCHAR(64) PRIMARY KEY,
    ticker VARCHAR(16) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    sector VARCHAR(64) NOT NULL,
    sentiment VARCHAR(16) NOT NULL,
    confidence_score DOUBLE PRECISION NOT NULL,
    embedding vector(4) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_research_hnsw ON market_research_reports 
USING hnsw (embedding vector_cosine_ops) 
WITH (m = 16, ef_construction = 64);