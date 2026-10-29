"""Measure independent source diffs; never modify the source repository/copies."""

import argparse
import hashlib
import json
import subprocess
from pathlib import Path


def measure(root: Path, output: Path) -> None:
    baseline = root / "baseline"
    prefix = Path("app/src/main/kotlin/ru/course/cityservices")
    baseline_files = sorted((baseline / prefix).rglob("*.kt"))
    digest = hashlib.sha256()
    for file in baseline_files:
        digest.update(file.relative_to(baseline).as_posix().encode())
        digest.update(b"\0")
        digest.update(file.read_bytes())
    result = {
        "baseline_commit": "dfb9704407b33641cd2cee13cbc43e0484c68cf4",
        "baseline_kotlin_sha256": digest.hexdigest(),
        "scope": prefix.as_posix() + "/**/*.kt",
        "line_metric": "git diff --no-index --ignore-space-at-eol --numstat; ignore CRLF/LF-only changes; include comments",
        "subsystem_metric": "distinct first package directory under ru/course/cityservices; UI screen/component count as one ui subsystem",
        "independent_exercises": True,
        "baseline_validation": {"task": ":app:compileDebugKotlin", "result": "BUILD SUCCESSFUL", "jdk": "Azul 21.0.13", "reported_seconds": 27, "executed_tasks": 7},
        "exercises": {},
    }
    for name in ("exercise-a", "exercise-b"):
        variant = root / name
        files = []
        patches = []
        all_paths = sorted({p.relative_to(baseline) for p in baseline_files} |
                           {p.relative_to(variant) for p in (variant / prefix).rglob("*.kt")})
        for relative in all_paths:
            before, after = baseline / relative, variant / relative
            if before.read_bytes() == after.read_bytes():
                continue
            args = ["git", "-c", "core.autocrlf=false", "diff", "--no-index", "--no-ext-diff", "--ignore-space-at-eol"]
            diff = subprocess.run(args + ["--", str(before), str(after)],
                                  capture_output=True, check=False)
            if diff.returncode != 1:
                raise RuntimeError(diff.stderr.decode("utf-8", errors="replace"))
            lines = diff.stdout.decode("utf-8").splitlines(keepends=True)
            repo_path = relative.as_posix()
            sanitized = []
            for line in lines:
                if line.startswith("diff --git "):
                    line = f"diff --git a/{repo_path} b/{repo_path}\n"
                elif line.startswith("--- "):
                    line = f"--- a/{repo_path}\n"
                elif line.startswith("+++ "):
                    line = f"+++ b/{repo_path}\n"
                sanitized.append(line)
            patches.append("".join(sanitized))
            numstat = subprocess.run(args + ["--numstat", "--", str(before), str(after)],
                                     capture_output=True, check=False)
            if numstat.returncode not in (0, 1) or not numstat.stdout:
                raise RuntimeError(numstat.stderr.decode("utf-8", errors="replace"))
            added, deleted, _ = numstat.stdout.decode("utf-8").split("\t", 2)
            files.append({"path": repo_path,
                          "subsystem": relative.relative_to(prefix).parts[0],
                          "added": int(added), "deleted": int(deleted)})
        patch = "".join(patches)
        if str(root).replace("\\", "/") in patch or "C:/Users/" in patch:
            raise RuntimeError("Unsanitized patch path")
        patch_name = name + ".patch"
        (output / patch_name).write_text(patch, encoding="utf-8", newline="\n")
        added = sum(f["added"] for f in files)
        deleted = sum(f["deleted"] for f in files)
        result["exercises"][name] = {
            "changed_files": len(files), "added_lines": added,
            "deleted_lines": deleted, "changed_lines": added + deleted,
            "net_lines": added - deleted,
            "subsystems": sorted({f["subsystem"] for f in files}),
            "subsystem_count": len({f["subsystem"] for f in files}),
            "files": files, "patch": patch_name,
            "patch_sha256": hashlib.sha256(patch.encode("utf-8")).hexdigest(),
            "validation": {
                "task": ":app:compileDebugKotlin", "result": "BUILD SUCCESSFUL",
                "jdk": "Azul 21.0.13", "executed_tasks": 7,
                "reported_seconds": 39 if name == "exercise-a" else 38,
                "runtime_tested": False, "migration_runtime_tested": False,
                "warning": "Room schema export directory not configured (existing baseline configuration)",
            },
        }
    (output / "measurements.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("isolated_root", type=Path)
    args = parser.parse_args()
    measure(args.isolated_root, Path(__file__).resolve().parent)
