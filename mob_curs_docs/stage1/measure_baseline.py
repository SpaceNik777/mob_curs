"""Repeatable source measurements and optional installed-APK launch samples.

No application data is cleared. --device restarts the installed application.
Counts are lexical source indicators, not compiler or coverage results.
"""
import argparse
import hashlib
import json
import os
import re
import statistics
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
OUT = HERE / "baseline"
OUT.mkdir(exist_ok=True)
SOURCE = ROOT / "app/src/main/kotlin"
parser = argparse.ArgumentParser()
parser.add_argument("--device", action="store_true")
args = parser.parse_args()


def clean(text):
    return re.sub(r"/\*.*?\*/|//[^\n]*", "", text, flags=re.S)


contents = {p.relative_to(ROOT).as_posix(): clean(p.read_text(encoding="utf-8")) for p in SOURCE.rglob("*.kt")}
raw_contents = {p:(ROOT/p).read_text(encoding="utf-8") for p in contents}
tests = list((ROOT / "app/src/test").rglob("*.kt"))
ui = {p:t for p,t in contents.items() if "/ui/" in p or p.endswith("CityServicesApp.kt")}
domain = {p:t for p,t in contents.items() if "/domain/" in p}
patterns = {
    "ui_locator_calls": (ui, r"\bServiceLocator\.(\w+)\s*\("),
    "ui_direct_storage_or_api_imports": (ui, r"^import\s+(?:androidx\.(?:room|datastore)\.|ru\.course\.cityservices\.(?:storage|network)\.)[^\n]+"),
    "domain_android_compose_room_imports": (domain, r"^import\s+(?:android\.|androidx\.)[^\n]+"),
    "appstate_external_mutations": ({p:t for p,t in contents.items() if not p.endswith("AppState.kt")},
        r"\bAppState\.\w+(?:\.(?:add|clear|remove)|\s*=|\s*\+=)|\bAppState\.(?:login|reset)\s*\("),
    "global_locator_calls": (contents, r"\bServiceLocator\.(\w+)\s*\("),
    "all_presentation_locator_calls": ({p:t for p,t in contents.items() if "/ui/" in p or p.endswith(("CityServicesApp.kt","MainActivity.kt"))}, r"\bServiceLocator\.(\w+)\s*\("),
    "address_validation_calls_outside_validator": ({p:t for p,t in contents.items() if not p.endswith("RequestValidator.kt")}, r"\b(?:validateAddress|RequestValidator\.validate)\s*\("),
    "notification_read_mutation": (ui, r"\b\w+\.isRead\s*=\s*true"),
}
indicators = {}
for name,(scope,pattern) in patterns.items():
    matches = [{"path":p,"line":raw_contents[p][:raw_contents[p].find(m.group(0))].count("\n")+1,"match":m.group(0)}
               for p,t in scope.items() for m in re.finditer(pattern,t,re.M)]
    indicators[name] = {"occurrences":len(matches),"files":len({m['path'] for m in matches}),"evidence":matches}
summary = json.loads((HERE / "analysis.json").read_text(encoding="utf-8"))
by_name = {Path(p).stem:t for p,t in contents.items()}
checks = {
    "history_reads_all_users": "db.requestDao().all()" in by_name["ServiceLocator"],
    "history_dao_returns_flow": bool(re.search(r"fun\s+(?:all|forUser)\b[^\n]*Flow",by_name["RequestDao"])),
    "request_has_sync_or_operation_id": bool(re.search(r"\b(?:operationId|idempotencyKey|syncState|syncStatus|pending)\b",by_name["Models"])),
    "notification_read_is_plain_var": bool(re.search(r"var\s+isRead\s*:\s*Boolean",by_name["Models"])),
    "activity_calls_locator_init": "ServiceLocator.init(" in by_name["MainActivity"],
    "init_creates_fake_api": "api = FakeApi()" in by_name["ServiceLocator"],
    "detail_uses_saved_state": bool(re.search(r"\b(?:rememberSaveable|SavedStateHandle)\b",by_name["ServiceDetailScreen"])),
    "exception_handlers_in_locator":len(re.findall(r"catch\s*\([^)]*Exception",by_name["ServiceLocator"])),
    "explicit_cancellation_handling_in_locator": "CancellationException" in by_name["ServiceLocator"],
}
result = {
    "measured_at_utc":datetime.now(timezone.utc).isoformat(),
    "commit":subprocess.check_output(["git","rev-parse","HEAD"],cwd=ROOT,text=True).strip(),
    "source_sha256":{p:hashlib.sha256((ROOT/p).read_bytes()).hexdigest() for p in sorted(contents)},
    "method":"Lexical source counts. Files, not classes, are dependency graph nodes. No coverage inferred.",
    "structure":{k:summary[k] for k in ["main_files","main_lines","import_edges","reference_edges","packages","package_edges","file_scc","package_scc"]},
    "metrics":summary["metrics"],
    "indicators":indicators,
    "source_contract_checks":checks,
    "tests":{"local_files":len(tests),"local_test_annotations":sum(len(re.findall(r"@Test\b",clean(p.read_text(encoding='utf-8')))) for p in tests),
             "instrumented_files":len(list((ROOT/'app/src/androidTest').rglob('*.kt'))),"coverage_percent":None},
}
if not args.device and (OUT / "measurements.json").exists():
    previous = json.loads((OUT / "measurements.json").read_text(encoding="utf-8"))
    for key in ("device", "launch"):
        if key in previous:
            result[key] = previous[key]
    if "device" in result:
        result["device"] = {k:v for k,v in result["device"].items() if k in {"model", "api", "size", "provenance"}}
    result["retained_device_measurement_utc"] = previous.get("retained_device_measurement_utc", previous.get("measured_at_utc"))
if args.device:
    adb = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
    def shell(*cmd):
        return subprocess.check_output([adb,"shell",*cmd],text=True,encoding="utf-8",errors="replace")
    result["device"] = {"model":shell("getprop","ro.product.model").strip(),
        "api":shell("getprop","ro.build.version.sdk").strip(),"size":shell("wm","size").strip(),
        "provenance":"Preinstalled APK; identity to source commit not established. No data reset; saved session and DB retained."}
    samples = []
    for i in range(5):
        shell("am","force-stop","ru.course.cityservices")
        output = shell("am","start","-W","-n","ru.course.cityservices/.MainActivity")
        (OUT / f"launch-{i+1}.txt").write_text(output,encoding="utf-8")
        times = {k:int(v) for k,v in re.findall(r"^(ThisTime|TotalTime|WaitTime):\s*(\d+)",output,re.M)}
        samples.append({"run":i+1,**times})
        time.sleep(3)
    totals = [s["TotalTime"] for s in samples if "TotalTime" in s]
    result["launch"] = {"samples":samples,"median_total_ms":statistics.median(totals) if totals else None,
        "min_total_ms":min(totals) if totals else None,"max_total_ms":max(totals) if totals else None,
        "meaning":"Android am start -W TotalTime: Activity launch, NOT time until catalog ready. Five sequential process-cold, cache-warm launches; not Macrobenchmark."}
(OUT / "measurements.json").write_text(json.dumps(result,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
print(json.dumps({"structure":result["structure"],"indicators":{k:{x:v[x] for x in ['occurrences','files']} for k,v in indicators.items()},"source_contract_checks":checks,
                  "tests":result["tests"],"launch":result.get('launch')},ensure_ascii=False,indent=2))
