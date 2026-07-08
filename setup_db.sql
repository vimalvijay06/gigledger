-- Run this file in psql as the postgres superuser
-- Save it, then run: psql -U postgres -f setup_db.sql

-- Create the database (skip if already exists)
SELECT 'CREATE DATABASE gigledger_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'gigledger_db')\gexec

-- Create the user (skip if already exists)
DO
$$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'gigledger_user') THEN
    CREATE USER gigledger_user WITH PASSWORD 'gigledger_pass';
  END IF;
END
$$;

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE gigledger_db TO gigledger_user;
ALTER DATABASE gigledger_db OWNER TO gigledger_user;

\echo 'Done! gigledger_db and gigledger_user are ready.'
