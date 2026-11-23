#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
git remote get-url upstream >/dev/null 2>&1 || git remote add upstream https://github.com/FatihMakes/Mark-LVII.git
git fetch upstream
git merge upstream/main --no-edit
python3 -m pip install -r requirements.txt
echo "Self-update complete. Restart the app."
