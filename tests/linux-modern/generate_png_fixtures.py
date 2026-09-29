from pathlib import Path
from PIL import Image

out = Path("output/test-results/png-fixtures")
out.mkdir(parents=True, exist_ok=True)
Image.new("RGB", (7, 5), (20, 100, 220)).save(out / "rgb.png")
Image.new("RGBA", (7, 5), (20, 100, 220, 91)).save(out / "rgba.png")
Image.new("L", (7, 5), 80).save(out / "gray.png", bits=4)
Image.new("LA", (7, 5), (80, 91)).save(out / "gray-alpha.png")
p = Image.new("P", (7, 5)); p.putpalette([255, 0, 0, 0, 255, 0] + [0] * 762); p.putdata([0, 1] * 17 + [0]); p.save(out / "palette.png", bits=1)
t = Image.new("RGB", (7, 5), (255, 0, 255)); t.save(out / "trns.png", transparency=(255, 0, 255))
Image.new("I;16", (7, 5), 40000).save(out / "16bit.png")
(out / "channel-logo.png").write_bytes((out / "palette.png").read_bytes())
data = (out / "rgba.png").read_bytes(); (out / "malformed.png").write_bytes(data[:35])
