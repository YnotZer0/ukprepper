import sys, pathlib
from playwright.sync_api import sync_playwright
d = pathlib.Path(sys.argv[1]); out = pathlib.Path(sys.argv[2]); out.mkdir(parents=True, exist_ok=True)
css = (d/"common.css").read_text()
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page()
    for svg in sorted(d.glob("*.svg")):
        s = svg.read_text().replace("<style>@import url(common.css);</style>", f"<style>{css}</style>")
        import re
        w,h = map(int, re.search(r'width="(\d+)" height="(\d+)"', s).groups())
        pg.set_viewport_size({"width": w, "height": h})
        pg.set_content(f"<html><body style='margin:0'>{s}</body></html>")
        pg.screenshot(path=str(out/(svg.stem+".png")), clip={"x":0,"y":0,"width":w,"height":h})
    b.close()
