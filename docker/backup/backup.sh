#!/usr/bin/env bash
# ==============================================================================
# Enterprise E-Commerce Database Backup Script (MySQL 8.4)
# Supports: Full Logical Consistent Snapshot, GTID tracking, Compression,
#           Integrity Checksumming, and Automatic Retention Management.
# Hostinger VPS Target: Ubuntu 24.04, 16 vCPU, 32GB RAM, NVMe Storage.
# ==============================================================================

set -euo pipefail

# --- Configuration & Defaults ---
MYSQL_HOST="${MYSQL_HOST:-mysql-primary}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD:-root_secure_pass_123}"
MYSQL_DATABASE="${MYSQL_DATABASE:-ecommerce_db}"

BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +'%Y%m%d_%H%M%S')"
BACKUP_FILENAME="${MYSQL_DATABASE}_backup_${TIMESTAMP}.sql.gz"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILENAME}"
CHECKSUM_PATH="${BACKUP_PATH}.sha256"
LOG_FILE="${BACKUP_DIR}/backup.log"

# --- Formatting Helpers ---
log() {
    local level="$1"
    local msg="$2"
    local ts
    ts="$(date +'%Y-%m-%d %H:%M:%S')"
    echo "[${ts}] [${level}] ${msg}" | tee -a "${LOG_FILE}"
}

log_info()  { log "INFO"  "$1"; }
log_warn()  { log "WARN"  "$1"; }
log_error() { log "ERROR" "$1"; }

# --- Verification & Setup ---
mkdir -p "${BACKUP_DIR}"

log_info "Starting enterprise database backup for database: '${MYSQL_DATABASE}'..."
log_info "Target destination: ${BACKUP_PATH}"

# Test MySQL Connectivity
if ! mysqladmin ping -h"${MYSQL_HOST}" -P"${MYSQL_PORT}" -u"${MYSQL_USER}" -p"${MYSQL_PASSWORD}" --silent; then
    log_error "Cannot connect to MySQL server at ${MYSQL_HOST}:${MYSQL_PORT}. Aborting backup."
    exit 1
fi

# --- Execute mysqldump with Enterprise Parameters ---
# Parameters:
# --single-transaction: Consistent snapshot for InnoDB without locking reads/writes.
# --quick: Stream rows directly, avoiding large buffer consumption in RAM.
# --source-data=2: Record binary log file and position (with GTID) as a comment for PITR.
# --set-gtid-purged=ON: Include GTID executed sets for exact replica/PITR positioning.
# --routines --triggers --events: Complete schema object coverage.
# --hex-blob: Safely dump binary attributes without corruption.
START_TIME="$(date +%s)"

mysqldump \
    -h"${MYSQL_HOST}" \
    -P"${MYSQL_PORT}" \
    -u"${MYSQL_USER}" \
    -p"${MYSQL_PASSWORD}" \
    --single-transaction \
    --quick \
    --source-data=2 \
    --set-gtid-purged=ON \
    --routines \
    --triggers \
    --events \
    --hex-blob \
    --default-character-set=utf8mb4 \
    "${MYSQL_DATABASE}" | gzip -c > "${BACKUP_PATH}"

END_TIME="$(date +%s)"
DURATION=$((END_TIME - START_TIME))

# Verify that file was created and is not empty
if [ ! -s "${BACKUP_PATH}" ]; then
    log_error "Backup failed or generated empty archive: ${BACKUP_PATH}"
    rm -f "${BACKUP_PATH}"
    exit 1
fi

BACKUP_SIZE="$(du -h "${BACKUP_PATH}" | awk '{print $1}')"
log_info "Backup created successfully in ${DURATION}s. Archive size: ${BACKUP_SIZE}."

# --- Generate SHA-256 Checksum ---
log_info "Calculating SHA-256 integrity checksum..."
sha256sum "${BACKUP_PATH}" | awk '{print $1}' > "${CHECKSUM_PATH}"
log_info "Integrity checksum written to: ${CHECKSUM_PATH} ($(cat "${CHECKSUM_PATH}"))"

# --- Prune Backups Older Than Retention Policy ---
log_info "Pruning backups older than ${RETENTION_DAYS} days in ${BACKUP_DIR}..."
PRUNED_COUNT=0
while IFS= read -r file; do
    if [ -n "${file}" ]; then
        log_info "Removing expired backup: ${file}"
        rm -f "${file}" "${file}.sha256"
        PRUNED_COUNT=$((PRUNED_COUNT + 1))
    fi
done < <(find "${BACKUP_DIR}" -type f -name "${MYSQL_DATABASE}_backup_*.sql.gz" -mtime +"${RETENTION_DAYS}")

log_info "Pruning complete. ${PRUNED_COUNT} expired archives purged."
log_info "Database backup cycle completed successfully."
exit 0

