#!/bin/bash

# Light backup script to avoid disk space issues.

TAG=$1
if [ -z "$TAG" ]; then
  echo "Usage: $0 <tag>"
  exit 1
fi

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="backups/light_${TAG}_${TIMESTAMP}"

# 1. Create directory
mkdir -p "$BACKUP_DIR"

echo "Created backup directory: $BACKUP_DIR"

# 2. List of files/dirs to copy
# Using an array to hold the list of files
declare -a FILES_TO_COPY=(
    ".idx/dev.nix"
    "server/src/index.js"
    "server/src/persistence.js"
    "lib/main.dart"
    "lib/app_config.dart"
    "docs/PROGRESS_LOG.md"
    ".agent_state.json"
    "assets/poc/world_objects.json"
    "assets/poc/world_objects_generated.json"
    "contentpacks/poc/world/generated/world_objects.json"
    "tools/web_dev_proxy.js"
    "tools/light_backup.sh"
)

# 3. Copy files, preserving directory structure relative to project root
for item in "${FILES_TO_COPY[@]}"; do
    if [ -e "$item" ]; then
        # Create the destination directory inside the backup folder
        mkdir -p "$BACKUP_DIR/$(dirname "$item")"
        # Copy the file/directory
        cp -r "$item" "$BACKUP_DIR/$item"
    else
        echo "Warning: $item does not exist, skipping."
    fi
done

# 4. Check size and decide whether to tar
TOTAL_SIZE_KB=$(du -sk "$BACKUP_DIR" | cut -f1)
SIZE_LIMIT_KB=5120 # 5MB

echo "Total backup size: ${TOTAL_SIZE_KB}KB"

if [ "$TOTAL_SIZE_KB" -lt "$SIZE_LIMIT_KB" ]; then
    echo "Size is under ${SIZE_LIMIT_KB}KB. Creating tar.gz archive..."
    tar -czf "${BACKUP_DIR}.tar.gz" -C "backups" "$(basename "$BACKUP_DIR")"
    rm -rf "$BACKUP_DIR"
    echo "Archive created: ${BACKUP_DIR}.tar.gz"
else
    echo "Size exceeds ${SIZE_LIMIT_KB}KB. Keeping directory instead of creating archive."
    echo "Total size: ${TOTAL_SIZE_KB}KB" > "$BACKUP_DIR/size_report.txt"
fi
