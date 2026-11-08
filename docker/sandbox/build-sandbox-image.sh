#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_NAME="${1:-${AGENT_SANDBOX_IMAGE:-x-agent-sandbox:1.0.0}}"

docker build \
  --tag "${IMAGE_NAME}" \
  "${SCRIPT_DIRECTORY}"
