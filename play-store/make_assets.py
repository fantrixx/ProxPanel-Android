from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
ASSETS = Path(
    r"C:\Users\Timur\.cursor\projects\c-Users-Timur-Documents-proxpanelAndroid-ProxPanel-Android\assets"
)
ROOT.mkdir(exist_ok=True)

ORANGE = (255, 122, 26)
DARK = (11, 13, 18)
BG = (10, 12, 16)
INK = (242, 245, 250)
MUTED = (148, 160, 180)
SURFACE = (18, 22, 30)


def font_bold(size: int) -> ImageFont.ImageFont:
    for name in (
        r"C:\Windows\Fonts\segoeuib.ttf",
        r"C:\Windows\Fonts\arialbd.ttf",
        r"C:\Windows\Fonts\arial.ttf",
    ):
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


def font_reg(size: int) -> ImageFont.ImageFont:
    for name in (
        r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arial.ttf",
    ):
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


def make_icon() -> None:
    # Exact brand mark matching app vector (108vp orange tile + 3 bars).
    icon = Image.new("RGB", (512, 512), ORANGE)
    d = ImageDraw.Draw(icon)
    s = 512 / 108

    def rect(x: float, y: float, w: float, h: float) -> None:
        d.rectangle((x * s, y * s, (x + w) * s, (y + h) * s), fill=DARK)

    rect(40, 44, 8, 28)
    rect(50, 36, 8, 36)
    rect(60, 50, 8, 22)
    path = ROOT / "icon-512.png"
    icon.save(path, "PNG")
    print("wrote", path)


def cover_crop(src: Image.Image, tw: int, th: int) -> Image.Image:
    scale = max(tw / src.width, th / src.height)
    nw, nh = int(src.width * scale), int(src.height * scale)
    resized = src.resize((nw, nh), Image.Resampling.LANCZOS)
    left = (nw - tw) // 2
    top = (nh - th) // 2
    return resized.crop((left, top, left + tw, top + th))


def make_feature() -> None:
    draft = ASSETS / "feature-graphic-draft.jpg"
    path = ROOT / "feature-graphic-1024x500.png"
    if draft.exists():
        src = Image.open(draft).convert("RGB")
        cover_crop(src, 1024, 500).save(path, "PNG")
        print("wrote", path, "(from AI draft)")
        return

    feat = Image.new("RGB", (1024, 500), BG)
    fd = ImageDraw.Draw(feat)
    fd.rounded_rectangle((48, 70, 560, 430), radius=28, fill=SURFACE)
    fd.rounded_rectangle((780, 140, 960, 320), radius=36, fill=ORANGE)
    fd.rectangle((812, 210, 836, 290), fill=DARK)
    fd.rectangle((848, 185, 872, 290), fill=DARK)
    fd.rectangle((884, 230, 908, 290), fill=DARK)
    fd.text((88, 160), "ProxPanel", fill=INK, font=font_bold(72))
    fd.text((88, 255), "Proxmox control on the go", fill=MUTED, font=font_reg(32))
    fd.text((88, 320), "Guests · Backups · Tasks · Storage", fill=ORANGE, font=font_reg(24))
    feat.save(path, "PNG")
    print("wrote", path)


def export_phone_shot(name: str, src_name: str) -> None:
    src_path = ASSETS / src_name
    if not src_path.exists():
        print("missing", src_path)
        return
    src = Image.open(src_path).convert("RGB")
    # Keep full mock including soft frame; Play accepts 9:16 phone shots.
    out = cover_crop(src, 1080, 1920)
    path = ROOT / name
    out.save(path, "PNG")
    print("wrote", path)


def main() -> None:
    make_icon()
    make_feature()
    export_phone_shot("screenshot-guests-1080x1920.png", "shot-guests-draft.jpg")
    export_phone_shot("screenshot-backups-1080x1920.png", "shot-backups-draft.jpg")
    export_phone_shot("screenshot-tasks-1080x1920.png", "shot-tasks-draft.jpg")
    print("all assets in", ROOT)


if __name__ == "__main__":
    main()
