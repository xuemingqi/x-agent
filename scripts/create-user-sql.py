#!/usr/bin/env python3
"""Generate one initial user INSERT; never connect to or overwrite a database."""
import argparse
import base64
import getpass
import hashlib
import hmac
import secrets


def sql_text(value):
    return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mobile", required=True)
    parser.add_argument("--name", default="User")
    parser.add_argument("--id", type=int, default=1)
    args = parser.parse_args()
    if args.id <= 0 or args.id > 2**63 - 1 or not args.mobile or len(args.mobile) > 32:
        parser.error("Use a positive BIGINT id and a mobile/login identifier of at most 32 characters")
    password = getpass.getpass("Initial user password: ")
    if len(password) < 12:
        parser.error("Use a password of at least 12 characters")
    if password != getpass.getpass("Confirm password: "):
        parser.error("Passwords do not match")
    salt = secrets.token_hex(16)
    digest = base64.b64encode(hmac.new(salt.encode(), password.encode(), hashlib.sha256).digest()).decode()
    values = [str(args.id), sql_text(args.name), sql_text(args.mobile), "0", sql_text(digest), sql_text(salt)]
    print("INSERT INTO `user` (id, name, mobile, sex, password, salt) VALUES (" + ", ".join(values) + ");")


if __name__ == "__main__":
    main()
