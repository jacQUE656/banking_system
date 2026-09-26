#!/bin/bash
set -e

for db in "$ACCOUNT_DB" "$TRANSACTION_DB" "$PAYMENT_DB"; do
  echo "Creating database: $db"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE $db OWNER $POSTGRES_USER;
EOSQL
done