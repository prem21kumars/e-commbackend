#!/usr/bin/env bash
# ==============================================================================
# Enterprise Point-In-Time Recovery (PITR) Script (MySQL 8.4)
# Performs: Base logical restore + optional binary log replay up to target timestamp/GTID.
# Target: Hostinger VPS Ubuntu 24.04 / Clustered Docker Compose.
# ==============================================================================

set -euo pipefail

# --- Configuration & Defaults ---
MYSQL_HOST="${MYSQL_HOST:-mysql-primary}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD:-root_secure_pass_123}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ecommerce_db}"

BACKUP_FILE="${1:-}"
STOP_DATETIME="${2:-}" # Format: 'YYYY-MM-DD HH:MM:SS'
BINLOG_DIR="${BINLOG_DIR:-/var/lib/mysql}"

log() {
    local level="$1"
    local msg="$2"
    local ts
    ts="$(date +'%Y-%m-%d %H:%M:%S')"
    echo "[${ts}] [${level}] ${msg}"
}

log_info()  { log "INFO"  "$1"; }
log_warn()  { log "WARN"  "$1"; }
log_error() { log "ERROR" "$1"; }

usage() {
    echo "Usage: $0 <backup-file.sql.gz> [stop-datetime]"
    echo ""
    echo "Examples:"
    echo "  $0 /backups/ecommerce_db_backup_20261031_040000.sql.gz"
    echo "  $0 /backups/ecommerce_db_backup_20261031_040000.sql.gz '2026-10-31 04:45:00'"
    exit 1
}

if [ -z "${BACKUP_FILE}" ]; then
    log_error "Missing backup file argument."
    usage
fi

if [ ! -f "${BACKUP_FILE}" ]; then
    log_error "Backup file does not exist: ${BACKUP_FILE}"
    exit 1
fi

# --- Integrity Check ---
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
if [ -f "${CHECKSUM_FILE}" ]; then
    log_info "Verifying SHA-256 integrity checksum..."
    EXPECTED_HASH="$(cat "${CHECKSUM_FILE}")"
    ACTUAL_HASH="$(sha256sum "${BACKUP_FILE}" | awk '{print $1}')"
    if [ "${EXPECTED_HASH}" != "${ACTUAL_HASH}" ]; then
        log_error "Checksum verification FAILED!"
        log_error "Expected: ${EXPECTED_HASH}"
        log_error "Actual:   ${ACTUAL_HASH}"
        exit 1
    fi
    log_info "Checksum OK: ${ACTUAL_HASH}"
else
    log_warn "Checksum file ${CHECKSUM_FILE} not found. Skipping integrity validation."
fi

# --- Safety Confirmation ---
log_warn "WARNING: This operation will OVERWRITE data in database '${MYSQL_DATABASE}' on host '${MYSQL_HOST}'!"
if [ -t 0 ] && [ "${FORCE_RESTORE:-0}" != "1" ]; then
    read -r -p "Are you sure you want to proceed with restore? [y/N]: " confirm
    if [[ ! "${confirm}" =~ ^[Yy]$ ]]; then
        log_info "Restore cancelled by operator."
        exit 0
    fi
fi

# --- 1. Restore Base Full Backup ---
log_info "Restoring base backup into '${MYSQL_DATABASE}'..."
START_RESTORE="$(date +%s)"

# Ensure target database exists
mysql -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" \
    -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Decompress and stream to MySQL
gzip -dc "${BACKUP_FILE}" | mysql \
    -h"${MYSQL_HOST}" \
    -P"${MYSQL_PORT}" \
    -u"${MYSQL_USER}" \
    -p"${MYSQL_PASSWORD}" \
    --default-character-set=utf8mb4 \
    "${MYSQL_DATABASE}"

END_RESTORE="$(date +%s)"
log_info "Base backup restored successfully in $((END_RESTORE - START_RESTORE))s."

# --- 2. Point-In-Time Binary Log Replay (If requested) ---
if [ -n "${STOP_DATETIME}" ]; then
    log_info "Starting Point-In-Time Recovery up to: ${STOP_DATETIME}..."
    
    # Extract START position or GTID from the source-data comment in the dump header
    BINLOG_INFO="$(gzip -dc "${BACKUP_FILE}" | head -n 50 | grep -E "CHANGE MASTER TO|SOURCE_TO" || true)"
    log_info "Base dump binary log coordinates: ${BINLOG_INFO}"

    # Locate binlogs in binlog directory
    if [ -d "${BINLOG_DIR}" ]; then
        log_info "Scanning binary logs in ${BINLOG_DIR}..."
        BINLOG_FILES="$(ls -1 "${BINLOG_DIR}"/binlog.[0-9]* 2>/dev/null || true)"
        
        if [ -n "${BINLOG_FILES}" ]; then
            log_info "Replaying binary logs up to timestamp '${STOP_DATETIME}'..."
            mysqlbinlog \
                --stop-datetime="${STOP_DATETIME}" \
                --database="${MYSQL_DATABASE}" \
                ${BINLOG_FILES} | mysql \
                    -h"${MYSQL_HOST}" \
                    -P"${MYSQL_PORT}" \
                    -u"${MYSQL_USER}" \
                    -p"${MYSQL_PASSWORD}" \
                    "${MYSQL_DATABASE}"
            log_info "Binary logs replayed successfully to point-in-time: ${STOP_DATETIME}."
        else
            log_warn "No binary logs found in ${BINLOG_DIR}. PITR replay skipped."
        fi
    else
        log_warn "Binlog directory '${BINLOG_DIR}' not mounted. PITR replay skipped."
    fi
fi

log_info "Point-in-time recovery procedure completed successfully."
exit 0

