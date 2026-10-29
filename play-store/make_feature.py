from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
ORANGE = (255, 122, 26)
DARK = (11, 13, 18)
BG = (10, 12, 16)
INK = (242, 245, 250)
MUTED = (148, 160, 180)
SURFACE = (18, 22, 30)
LINE = (46, 55, 72)


def load_font(candidates, size):
    for name in candidates:
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


def draw_logo(size: int) -> Image.Image:
    """Exact app mark: orange tile + 3 dark bars (same as launcher vector)."""
    img = Image.new("RGB", (size, size), ORANGE)
    d = ImageDraw.Draw(img)
    s = size / 108

    def rect(x: float, y: float, w: float, h: float) -> None:
        d.rectangle((x * s, y * s, (x + w) * s, (y + h) * s), fill=DARK)

    rect(40, 44, 8, 28)
    rect(50, 36, 8, 36)
    rect(60, 50, 8, 22)
    return img


def rounded_paste(base: Image.Image, tile: Image.Image, xy: tuple[int, int], radius: int) -> None:
    mask = Image.new("L", tile.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, tile.size[0] - 1, tile.size[1] - 1),
        radius=radius,
        fill=255,
    )
    base.paste(tile, xy, mask)


def main() -> None:
    w, h = 1024, 500
    canvas = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(canvas)

    d.rounded_rectangle((40, 48, 620, 452), radius=28, fill=SURFACE, outline=LINE, width=1)
    d.rounded_rectangle((40, 48, 52, 452), radius=6, fill=ORANGE)

    bold = load_font((r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf"), 78)
    reg = load_font((r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"), 30)
    small = load_font((r"C:\Windows\Fonts\segoeui.ttf", r"C:\Windows\Fonts\arial.ttf"), 22)

    d.text((88, 130), "ProxPanel", fill=INK, font=bold)
    d.text((88, 230), "Proxmox control on the go", fill=MUTED, font=reg)
    d.text((88, 300), "Guests  ·  Backups  ·  Tasks  ·  Storage", fill=ORANGE, font=small)

    logo = draw_logo(220)
    rounded_paste(canvas, logo, (720, 140), radius=48)

    out = ROOT / "feature-graphic-1024x500.png"
    canvas.save(out, "PNG")
    print("wrote", out)


if __name__ == "__main__":
    main()
