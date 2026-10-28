"""Check generated evidence, report links, metrics and PNG integrity."""

import csv
import json
import re
from pathlib import Path
from urllib.parse import unquote

import networkx as nx
from PIL import Image


HERE = Path(__file__).resolve().parent
summary = json.loads((HERE / "analysis.json").read_text(encoding="utf-8"))
with (HERE / "edges.csv").open(encoding="utf-8-sig", newline="") as stream:
    rows = list(csv.DictReader(stream))
graph = nx.DiGraph()
graph.add_nodes_from(summary["files"])
graph.add_edges_from((r["consumer"], r["dependency"]) for r in rows)
assert len(graph) == summary["main_files"]
assert graph.number_of_edges() == summary["reference_edges"]
assert sum(v["lines"] for v in summary["files"].values()) == summary["main_lines"]
assert list(nx.simple_cycles(graph)) == summary["file_cycles"]
for metric in summary["metrics"]:
    name = metric["file"]
    assert metric["fan_in"] == graph.in_degree(name)
    assert metric["fan_out"] == graph.out_degree(name)
    assert metric["transitive_dependents"] == len(nx.ancestors(graph, name))

reports = list(HERE.glob("*.md"))
assert len(reports) == 1
report = reports[0].read_text(encoding="utf-8")
image_links = re.findall(r"!\[[^\]]*\]\(([^)]+)\)", report)
assert len(image_links) == 11 and len(set(image_links)) == 11
for link in image_links:
    path = HERE / unquote(link)
    with Image.open(path) as image:
        assert image.format == "PNG"
        assert image.width >= 1500 and image.height >= 1000
        print(path.name, image.size)
        image.verify()

for row in summary["metrics"]:
    pattern = rf"\| `{re.escape(row['file'])}` \| (\d+) \| (\d+) \| (\d+) \| (\d+) \|"
    match = re.search(pattern, report)
    if match:
        expected = tuple(row[k] for k in ("lines", "fan_in", "fan_out", "transitive_dependents"))
        assert tuple(map(int, match.groups())) == expected, row["file"]
print("PASS: graph evidence, metrics table, 11 image links and PNG integrity")
