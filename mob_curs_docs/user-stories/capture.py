"""Capture the connected emulator and annotate real application screenshots."""
import os
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
ADB = str(Path(os.environ["LOCALAPPDATA"]) / "Android/Sdk/platform-tools/adb.exe")
RAW = HERE / "originals"
IMAGES = HERE / "images"
RAW.mkdir(exist_ok=True)
IMAGES.mkdir(exist_ok=True)


def capture(name):
    data = subprocess.check_output([ADB, "exec-out", "screencap", "-p"])
    (RAW / f"{name}.png").write_bytes(data)


def annotate(name, source, title, instruction, bounds):
    image = Image.open(RAW / f"{source}.png").convert("RGB")
    w,h = image.size
    font_path = "C:/Windows/Fonts/arial.ttf"
    font = ImageFont.truetype(font_path, 27)
    small = ImageFont.truetype(font_path, 23)
    canvas = Image.new("RGB", (w+480,h), "#f1f5f9")
    canvas.paste(image,(0,0))
    draw = ImageDraw.Draw(canvas)
    draw.text((w+26,50),title,font=font,fill="#17365a")
    import textwrap
    lines = textwrap.wrap(instruction,width=30)
    draw.multiline_text((w+26,110),"\n".join(lines),font=small,fill="#334155",spacing=10)
    if bounds:
        x1,y1,x2,y2 = bounds
        draw.rounded_rectangle(bounds,radius=10,outline="#dc2626",width=6)
        draw.line([(w+18,260),(w-12,260),(x2+10,(y1+y2)//2)],fill="#dc2626",width=4)
        draw.ellipse((x2-12,(y1+y2)//2-9,x2+6,(y1+y2)//2+9),fill="#dc2626")
    draw.multiline_text((w+26,h-160),"Реальный экран приложения\nКрасная рамка: куда смотреть\nили где выполнить действие",font=ImageFont.truetype(font_path,19),fill="#64748b",spacing=8)
    canvas.save(IMAGES / f"{name}.png")


if __name__ == "__main__":
    if sys.argv[1] == "capture":
        capture(sys.argv[2])
    else:
        raise SystemExit("Usage: python capture.py capture NAME")
