#!/bin/bash
# Creates planning-service's own schema and a user that can only access that schema
# (ADR-0003: each service owns its data).
#
# Runs once, when the MySQL container initialises an empty data volume. The official image
# either *runs* this file (if it's executable, as files from Windows usually are) or *sources*
# it, so it calls the mysql client directly instead of relying on the entrypoint's helpers.
# Used by docker compose AND by planning-service's Testcontainers tests, so the tests prove it.

if [ -z "${PLANNING_DB_PASSWORD:-}" ]; then
    echo >&2 "02-planning-db.sh: PLANNING_DB_PASSWORD is not set"
    exit 1
fi

# MYSQL_PWD keeps the root password off the command line (and out of `ps` output).
# The temporary init server listens on the default socket only.
MYSQL_PWD="${MYSQL_ROOT_PASSWORD:-}" mysql --protocol=socket -uroot <<EOSQL
CREATE DATABASE IF NOT EXISTS \`planning\`;
CREATE USER IF NOT EXISTS 'planning'@'%' IDENTIFIED BY '${PLANNING_DB_PASSWORD}';
GRANT ALL PRIVILEGES ON \`planning\`.* TO 'planning'@'%';
EOSQL
