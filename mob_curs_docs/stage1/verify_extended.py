"""Validate unchanged production sources and privacy of extended evidence.

This checks known identifying patterns, not a guarantee of secret detection.
Only explicitly published evidence files are inspected, not local build caches.
"""
import hashlib
import json
import re
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
if not __debug__:
    raise SystemExit("Run verification without Python -O: assertions must be enabled")
if not (HERE / "extended-baseline").is_dir():
    raise SystemExit("Missing extended-baseline evidence directory")
baseline = json.loads((HERE / "baseline/measurements.json").read_text(encoding="utf-8"))
for name, expected in baseline["source_sha256"].items():
    actual = hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
    assert actual == expected, f"Production source changed: {name}"
expected_names = set(baseline["source_sha256"])
actual_names = {p.relative_to(ROOT).as_posix() for p in (ROOT / "app/src/main/kotlin").rglob("*.kt")}
assert actual_names == expected_names, "Production file inventory changed"
assert not subprocess.check_output(["git", "diff", "HEAD", "--", "app", "build.gradle.kts", "settings.gradle.kts", "gradle"], cwd=ROOT), "Tracked application/configuration changed"

patterns = {
    "personal Windows home": r"[A-Z]:[\\/]+Users[\\/]+(?!<|\$|PUBLIC\b|USER\b|REDACTED\b)[^\\/\s\"']+",
    "computer name": r"\bDESKTOP-[A-Z0-9]+\b",
    "GitHub credential": r"\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]+)\b",
    "private key": r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
}
count = 0
findings = []
for path in sorted((HERE / "extended-baseline").rglob("*")):
    if not path.is_file() or path.suffix.lower() in {".png", ".jar", ".zip", ".apk", ".exec", ".ec", ".pyc"}:
        continue
    data = path.read_bytes()
    text = data.decode("utf-16") if data.startswith((b"\xff\xfe", b"\xfe\xff")) else data.decode("utf-8-sig")
    for label, pattern in patterns.items():
        if re.search(pattern, text, re.I):
            findings.append(f"{path.relative_to(HERE)}: {label}")
    if path.suffix == ".json":
        json.loads(text)
    if path.suffix == ".xml":
        ET.fromstring(text)
    count += 1
assert not findings, "\n".join(findings)
for report in (HERE / "extended-baseline").rglob("*.md"):
    text = report.read_text(encoding="utf-8-sig")
    for link in re.findall(r"!?\[[^\]]*\]\(([^)]+)\)",text):
        if "://" not in link and not link.startswith("#"):
            from urllib.parse import unquote
            assert (report.parent / unquote(link.split('#')[0])).exists(), f"Broken evidence link in {report.name}"
print(f"PASS: {len(baseline['source_sha256'])} production hashes unchanged; {count} evidence text files checked")
