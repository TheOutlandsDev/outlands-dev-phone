"""Verify an Outlands evidence handoff ZIP locally without extracting files."""

import argparse
import base64
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import stat
import sys
import tempfile
import zipfile

MAX_FILES = 16
MAX_FILE_BYTES = 64 * 1024 * 1024
MAX_TOTAL_BYTES = 128 * 1024 * 1024
MAX_ARCHIVE_BYTES = MAX_TOTAL_BYTES + 2 * 1024 * 1024
MAX_MANIFEST_BYTES = 16384
MAX_RECEIPT_BYTES = 4096
SHA256 = re.compile(r"[0-9a-f]{64}")
P256_SPKI_PREFIX = bytes.fromhex("3059301306072a8648ce3d020106082a8648ce3d03010703420004")
UTC_TIME = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z")


def unique_object(pairs):
    obj = {}
    for key, value in pairs:
        if key in obj:
            raise ValueError("The JSON contains a repeated key.")
        obj[key] = value
    return obj


def read_manifest(archive):
    info = archive.getinfo("manifest.json")
    if info.file_size > MAX_MANIFEST_BYTES:
        raise ValueError("The manifest exceeds the size limit.")
    with archive.open(info) as source:
        data = source.read(MAX_MANIFEST_BYTES + 1)
    manifest = json.loads(data.decode("utf-8"), object_pairs_hook=unique_object)
    if (not isinstance(manifest, dict) or set(manifest) != {"schemaVersion", "files"}
            or type(manifest["schemaVersion"]) is not int or manifest["schemaVersion"] != 1):
        raise ValueError("Unsupported manifest schema.")

    records = manifest["files"]
    if not isinstance(records, list) or not 1 <= len(records) <= MAX_FILES:
        raise ValueError("The manifest must describe between 1 and 16 files.")

    total = 0
    for index, record in enumerate(records, start=1):
        if not isinstance(record, dict) or set(record) != {"name", "sha256", "bytes"}:
            raise ValueError("Malformed file record.")
        name, digest, size = record["name"], record["sha256"], record["bytes"]
        if (not isinstance(name, str) or not re.fullmatch(rf"evidence-{index:02d}\.(bin|png|jpg)", name)
                or not isinstance(digest, str) or not SHA256.fullmatch(digest)
                or type(size) is not int or not 0 <= size <= MAX_FILE_BYTES):
            raise ValueError("Malformed filename, SHA-256, or byte count.")
        total += size
    if total > MAX_TOTAL_BYTES:
        raise ValueError("The handoff exceeds the total byte limit.")
    return records


def read_receipt(path):
    with path.open("rb") as source:
        data = source.read(MAX_RECEIPT_BYTES + 1)
    if len(data) > MAX_RECEIPT_BYTES:
        raise ValueError("The receipt exceeds the size limit.")
    receipt = json.loads(data.decode("utf-8"), object_pairs_hook=unique_object)
    keys = {"schemaVersion", "zipSha256", "zipBytes", "exportedAtUtc"}
    if not isinstance(receipt, dict) or type(receipt.get("schemaVersion")) is not int:
        raise ValueError("Unsupported receipt schema.")
    version = receipt["schemaVersion"]
    if version == 2:
        keys |= {"signerKey", "signature"}
    if version not in (1, 2) or set(receipt) != keys:
        raise ValueError("Unsupported receipt schema.")
    digest, size, exported = receipt["zipSha256"], receipt["zipBytes"], receipt["exportedAtUtc"]
    if (not isinstance(digest, str) or not SHA256.fullmatch(digest)
            or type(size) is not int or not 0 < size <= MAX_ARCHIVE_BYTES
            or not isinstance(exported, str) or not UTC_TIME.fullmatch(exported)):
        raise ValueError("Malformed receipt SHA-256, byte count, or UTC time.")
    try:
        datetime.fromisoformat(exported[:-1] + "+00:00")
    except ValueError as error:
        raise ValueError("Malformed receipt UTC time.") from error
    return receipt


def decode_receipt_base64(value):
    if not isinstance(value, str):
        raise ValueError("Invalid receipt signature.")
    decoded = base64.b64decode(value, validate=True)
    if base64.b64encode(decoded).decode("ascii") != value:
        raise ValueError("Invalid receipt signature.")
    return decoded


def verify_receipt_signature(receipt, trusted_signer):
    if receipt["schemaVersion"] == 1:
        if trusted_signer is not None:
            raise ValueError("A signed receipt from the trusted signer is required.")
        return

    try:
        from cryptography.exceptions import InvalidSignature
        from cryptography.hazmat.primitives import hashes, serialization
        from cryptography.hazmat.primitives.asymmetric import ec
    except ImportError as error:
        raise ValueError("Install the Python cryptography package to verify signed receipts.") from error

    try:
        public_key = decode_receipt_base64(receipt["signerKey"])
        signature = decode_receipt_base64(receipt["signature"])
        if len(public_key) != 91 or not public_key.startswith(P256_SPKI_PREFIX) or not 8 <= len(signature) <= 72:
            raise ValueError("Invalid receipt signature.")
        fingerprint = hashlib.sha256(public_key).hexdigest()
        payload = (
            f"OUTLANDS-HANDOFF-RECEIPT-V2\n{receipt['zipSha256']}\n{receipt['zipBytes']}\n"
            f"{receipt['exportedAtUtc']}\n{fingerprint}\n"
        ).encode("ascii")
        key = serialization.load_der_public_key(public_key)
        key.verify(signature, payload, ec.ECDSA(hashes.SHA256()))
    except (InvalidSignature, ValueError) as error:
        raise ValueError("Invalid receipt signature.") from error
    if fingerprint != trusted_signer:
        raise ValueError("Unknown signer (SHA-256): " + fingerprint)


def check_receipt(source, snapshot, receipt):
    digest = hashlib.sha256()
    size = 0
    for block in iter(lambda: source.read(65536), b""):
        size += len(block)
        if size > receipt["zipBytes"]:
            raise ValueError("ZIP byte count differs from the receipt.")
        digest.update(block)
        snapshot.write(block)
    if size != receipt["zipBytes"]:
        raise ValueError("ZIP byte count differs from the receipt.")
    if digest.hexdigest() != receipt["zipSha256"]:
        raise ValueError("ZIP SHA-256 differs from the receipt.")
    snapshot.seek(0)


def verify(path, receipt_path=None, trusted_signer=None):
    if receipt_path is None:
        if trusted_signer is not None:
            raise ValueError("A trusted signer requires a receipt.")
        if path.stat().st_size > MAX_ARCHIVE_BYTES:
            raise ValueError("The ZIP exceeds the size limit.")
        return verify_archive(path)

    receipt = read_receipt(receipt_path)
    verify_receipt_signature(receipt, trusted_signer)
    # Parse the exact bytes just checked, even if the input file is replaced later.
    with path.open("rb") as source, tempfile.SpooledTemporaryFile(max_size=2 * 1024 * 1024) as snapshot:
        check_receipt(source, snapshot, receipt)
        return verify_archive(snapshot)


def verify_archive(zip_source):
    with zipfile.ZipFile(zip_source, "r") as archive:
        entries = archive.infolist()
        if len(entries) > MAX_FILES + 1:
            raise ValueError("The ZIP includes too many entries.")
        names = set()
        for info in entries:
            file_type = stat.S_IFMT(info.external_attr >> 16)
            if (info.filename in names or info.orig_filename != info.filename
                    or info.is_dir() or file_type not in (0, stat.S_IFREG)
                    or info.flag_bits & 1 or info.compress_type not in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED)):
                raise ValueError("The ZIP includes a duplicate or unsupported entry.")
            names.add(info.filename)
        if "manifest.json" not in names:
            raise ValueError("Missing archive entry: manifest.json")

        records = read_manifest(archive)
        expected = {"manifest.json", *(item["name"] for item in records)}
        missing = expected - names
        if missing:
            raise ValueError("Missing archive entry: " + ", ".join(sorted(missing)))
        if names != expected:
            raise ValueError("The ZIP includes an entry absent from the manifest.")

        for record in records:
            name = record["name"]
            info = archive.getinfo(name)
            if info.file_size != record["bytes"]:
                raise ValueError("Byte count mismatch: " + name)
            digest = hashlib.sha256()
            count = 0
            with archive.open(info) as source:
                for block in iter(lambda: source.read(65536), b""):
                    count += len(block)
                    if count > record["bytes"]:
                        raise ValueError("Byte count mismatch: " + name)
                    digest.update(block)
            if count != record["bytes"]:
                raise ValueError("Byte count mismatch: " + name)
            if digest.hexdigest() != record["sha256"]:
                raise ValueError("SHA-256 mismatch: " + name)
    return len(records)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path, help="Outlands evidence handoff ZIP")
    parser.add_argument("--receipt", type=Path, help="ZIP receipt received through a separate trusted channel")
    parser.add_argument("--trusted-signer-sha256", type=str.lower,
                        help="Signer fingerprint independently confirmed through a trusted channel")
    args = parser.parse_args()
    if args.trusted_signer_sha256 is not None and not SHA256.fullmatch(args.trusted_signer_sha256):
        parser.error("--trusted-signer-sha256 must be a 64-character SHA-256 fingerprint")
    try:
        count = verify(args.archive, args.receipt, args.trusted_signer_sha256)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"FAILED: {error}", file=sys.stderr)
        return 1
    if args.trusted_signer_sha256 is not None:
        print("Receipt signature matches the trusted signer.")
    if args.receipt is not None:
        print("ZIP SHA-256 and byte count match the supplied receipt.")
    print(f"Verified {count} file{'s' if count != 1 else ''}. SHA-256 and byte counts match the manifest.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

