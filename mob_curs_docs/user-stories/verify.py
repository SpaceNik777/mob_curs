"""Validate all user-story steps, screenshots and annotation bounds."""
import re
from PIL import Image
from annotate import STEPS
from capture import HERE

report = (HERE / "Пользовательские истории.md").read_text(encoding="utf-8")
links = re.findall(r"!\[[^\]]*\]\(([^)]+)\)", report)
assert len(links) == len(STEPS) == 30
assert len(set(links)) == 30
assert len(re.findall(r"^## US-\d+\.", report, re.M)) == 9
assert len(re.findall(r"^### Шаг \d+\.", report, re.M)) == 30
for name, source, _, _, bounds in STEPS:
    assert f"images/{name}.png" in links
    with Image.open(HERE / "originals" / f"{source}.png") as original:
        assert original.size == (1080,2220)
    with Image.open(HERE / "images" / f"{name}.png") as image:
        assert image.size == (1560,2220)
        image.verify()
    if bounds:
        x1,y1,x2,y2 = bounds
        assert 0 <= x1 < x2 <= 1080 and 0 <= y1 < y2 <= 2220
print("PASS: 9 stories, 30 steps, 30 annotated PNGs, original captures and bounds")
