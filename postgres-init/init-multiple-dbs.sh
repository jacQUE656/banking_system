#!/bin/bash
set -e

for db in bank_account_db transaction_db payment_db; do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE $db OWNER $POSTGRES_USER;
EOSQL
done