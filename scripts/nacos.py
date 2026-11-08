#!/usr/bin/env python3
"""Package, import and verify the public Nacos templates (Python standard library)."""
import argparse
import io
import json
import os
from pathlib import Path
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def configurations():
    base = ROOT / "config/nacos"
    for path in sorted(base.glob("*.yaml")):
        yield os.environ.get("NACOS_GROUP", "DEFAULT_GROUP"), path.name, "yaml", path.read_bytes()
    for path in sorted((base / "SENTINEL_GROUP").glob("*")):
        if path.is_file():
            yield "SENTINEL_GROUP", path.name, "json", path.read_bytes()


def archive(configs):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as result:
        metadata = []
        for group, data_id, kind, content in configs:
            result.writestr(f"{group}/{data_id}", content)
            metadata.append(dict(appName="x-agent", dataId=data_id, desc="X Agent configuration template",
                                 group=group, type=kind))
        # JSON is valid YAML, and Nacos 2.x reads this exact metadata filename.
        result.writestr(".metadata.yml", json.dumps(dict(metadata=metadata), indent=2) + "\n")
    return stream.getvalue()


class Nacos:
    def __init__(self):
        address = os.environ.get("NACOS_SERVER_ADDR", "127.0.0.1:8848")
        if "://" not in address:
            address = "http://" + address
        self.base = address.rstrip("/")
        if not self.base.endswith("/nacos"):
            self.base += "/nacos"
        self.namespace = os.environ.get("NACOS_NAMESPACE", "x-agent-dev")
        if not self.namespace or self.namespace == "public":
            raise ValueError("Set NACOS_NAMESPACE to a dedicated non-public project namespace")
        self.token = None
        username = os.environ.get("NACOS_USERNAME")
        if username:
            auth = self.request("/v1/auth/login", "POST", dict(username=username,
                                password=os.environ.get("NACOS_PASSWORD", "")))
            self.token = json.loads(auth)["accessToken"]

    def request(self, path, method="GET", params=None, body=None, headers=None):
        params = dict(params or {})
        if self.token:
            params["accessToken"] = self.token
        headers = dict(headers or {})
        url = self.base + path
        if method == "POST" and body is None:
            body = urllib.parse.urlencode(params).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            url += "?" + urllib.parse.urlencode(params)
        request = urllib.request.Request(url, data=body, headers=headers, method=method)
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.read()

    def ensure_namespace(self, create):
        response = json.loads(self.request("/v1/console/namespaces"))
        if any(item["namespace"] == self.namespace for item in response["data"]):
            return
        if not create:
            raise ValueError("Namespace does not exist; use --create-namespace to create it")
        result = self.request("/v1/console/namespaces", "POST", dict(customNamespaceId=self.namespace,
                              namespaceName=self.namespace, namespaceDesc="X Agent isolated environment"))
        if result.strip() != b"true":
            raise ValueError("Nacos did not confirm namespace creation")

    def content(self, group, data_id):
        try:
            return self.request("/v1/cs/configs", params=dict(tenant=self.namespace, group=group, dataId=data_id))
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return None
            raise

    def publish_archive(self, configs, overwrite):
        # Check all conflicts before sending any changes.
        for group, data_id, _, content in configs:
            existing = self.content(group, data_id)
            if existing is not None and existing != content and not overwrite:
                raise ValueError(f"{group}/{data_id} differs; review it before using --overwrite")
        boundary = "x-agent-" + uuid.uuid4().hex
        body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; "
                "filename=\"x-agent-nacos.zip\"\r\nContent-Type: application/zip\r\n\r\n").encode()
        body += archive(configs) + f"\r\n--{boundary}--\r\n".encode()
        response = json.loads(self.request("/v1/cs/configs", "POST",
            dict(**{"import": "true"}, namespace=self.namespace, tenant=self.namespace,
                 policy="OVERWRITE" if overwrite else "SKIP"),
            body, {"Content-Type": f"multipart/form-data; boundary={boundary}"}))
        if response.get("code") not in (200, 0):
            raise ValueError("Nacos rejected archive import; inspect the server console")

    def verify(self, configs):
        # Nacos publishing and its read cache converge asynchronously.
        pending = list(configs)
        for attempt in range(10):
            pending = [item for item in pending if self.content(item[0], item[1]) != item[3]]
            if not pending:
                print(f"Verified {len(configs)} configurations in namespace {self.namespace}")
                return
            if attempt < 9:
                time.sleep(1)
        raise ValueError("Verification failed: " + ", ".join(f"{item[0]}/{item[1]}" for item in pending))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["package", "import", "verify"])
    parser.add_argument("--output", type=Path, default=ROOT / "config/x-agent-nacos.zip")
    parser.add_argument("--create-namespace", action="store_true")
    parser.add_argument("--overwrite", action="store_true")
    args = parser.parse_args()
    configs = list(configurations())
    if args.command == "package":
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(archive(configs))
        print(f"Packaged {len(configs)} configurations: {args.output}")
        return
    client = Nacos()
    client.ensure_namespace(args.create_namespace)
    if args.command == "import":
        client.publish_archive(configs, args.overwrite)
    client.verify(configs)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        sys.exit(f"Nacos HTTP {error.code}; check address, credentials and namespace permissions")
    except urllib.error.URLError:
        sys.exit("Cannot reach Nacos; check NACOS_SERVER_ADDR and network connectivity")
    except (ValueError, KeyError) as error:
        sys.exit(str(error))
