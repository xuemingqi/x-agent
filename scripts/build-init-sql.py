#!/usr/bin/env python3
"""Build the schema-only SQL bundle for a fresh, empty X Agent database."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
sources = [
    "x-services/x-contact/src/main/resources/db/user-schema.sql",
    "x-services/x-ai/src/main/resources/db/agent-schema.sql",
    "x-services/x-ai/src/main/resources/db/mcp-schema.sql",
    "x-services/x-ai/src/main/resources/db/quartz-schema.sql",
]
target = root / "config/sql/x-agent-init.sql"
target.parent.mkdir(parents=True, exist_ok=True)
target.write_text("-- X Agent: run once against a fresh MySQL 8 database. No user data included.\n"
                  "SET NAMES utf8mb4;\n\n" + "\n\n".join(
                      "-- Source: " + source + "\n" + (root / source).read_text() for source in sources).rstrip() + "\n")
print(target)
