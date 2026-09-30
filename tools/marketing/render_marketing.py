from __future__ import annotations
import math
import os
import shutil
import subprocess
import wave
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageOps
ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / 'work' / 'marketing-rebuild'
OUT.mkdir(parents=True, exist_ok=True)
W, H, FPS = (720, 1280, 18)
CREAM = (255, 249, 240)
INK = (36, 50, 46)
MUTED = (99, 113, 105)
SAGE = (168, 198, 160)
PALE = (237, 243, 233)
APRICOT = (247, 184, 147)
PINK = (246, 170, 162)
WHITE = (255, 255, 255)
FONT = os.environ.get('JIGEUM_MARKETING_FONT', 'C:/Windows/Fonts/malgun.ttf')
BOLD = os.environ.get('JIGEUM_MARKETING_FONT_BOLD', 'C:/Windows/Fonts/malgunbd.ttf')
font_cache = {}

def f(size, bold=False):
    key = (size, bold)
    if key not in font_cache:
        font_cache[key] = ImageFont.truetype(BOLD if bold else FONT, size)
    return font_cache[key]

def text(draw, xy, value, size, fill=INK, bold=False, anchor='mm', spacing=8):
    draw.multiline_text(xy, value, font=f(size, bold), fill=fill, anchor=anchor, align='center' if anchor.endswith('m') else 'left', spacing=spacing)

def rr(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius, fill=fill, outline=outline, width=width)

def base(color=CREAM):
    im = Image.new('RGBA', (W, H), color + (255,))
    d = ImageDraw.Draw(im)
    d.ellipse((-200, -220, 350, 280), fill=(255, 238, 223, 255))
    d.ellipse((560, 950, 980, 1390), fill=(232, 241, 226, 255))
    return im

def sparkle(d, x, y, r, color=APRICOT):
    d.polygon([(x, y - r), (x + r * 0.22, y - r * 0.22), (x + r, y), (x + r * 0.22, y + r * 0.22), (x, y + r), (x - r * 0.22, y + r * 0.22), (x - r, y), (x - r * 0.22, y - r * 0.22)], fill=color)

def baby(d, cx, cy, scale=1, smile=False, wave=0, blush=True):
    s = scale
    rr(d, (cx - 185 * s, cy + 125 * s, cx + 185 * s, cy + 415 * s), 120 * s, (250, 237, 216), outline=INK, width=max(2, int(4 * s)))
    d.ellipse((cx - 210 * s, cy + 245 * s, cx - 95 * s, cy + 355 * s), fill=(255, 213, 184), outline=INK, width=max(2, int(4 * s)))
    d.ellipse((cx + 95 * s, cy + 245 * s - 20 * wave, cx + 210 * s, cy + 355 * s - 20 * wave), fill=(255, 213, 184), outline=INK, width=max(2, int(4 * s)))
    d.ellipse((cx - 208 * s, cy - 35 * s, cx - 138 * s, cy + 58 * s), fill=(255, 219, 194), outline=INK, width=max(2, int(4 * s)))
    d.ellipse((cx + 138 * s, cy - 35 * s, cx + 208 * s, cy + 58 * s), fill=(255, 219, 194), outline=INK, width=max(2, int(4 * s)))
    d.ellipse((cx - 180 * s, cy - 190 * s, cx + 180 * s, cy + 185 * s), fill=(255, 222, 198), outline=INK, width=max(2, int(5 * s)))
    d.arc((cx - 38 * s, cy - 217 * s, cx + 43 * s, cy - 145 * s), 180, 350, fill=INK, width=max(3, int(6 * s)))
    d.arc((cx - 3 * s, cy - 228 * s, cx + 66 * s, cy - 145 * s), 185, 355, fill=INK, width=max(3, int(5 * s)))
    if smile:
        d.arc((cx - 99 * s, cy - 28 * s, cx - 38 * s, cy + 28 * s), 5, 170, fill=INK, width=max(3, int(7 * s)))
        d.arc((cx + 38 * s, cy - 28 * s, cx + 99 * s, cy + 28 * s), 5, 170, fill=INK, width=max(3, int(7 * s)))
        d.arc((cx - 44 * s, cy + 42 * s, cx + 44 * s, cy + 119 * s), 5, 175, fill=INK, width=max(4, int(8 * s)))
    else:
        d.ellipse((cx - 72 * s, cy - 10 * s, cx - 54 * s, cy + 17 * s), fill=INK)
        d.ellipse((cx + 54 * s, cy - 10 * s, cx + 72 * s, cy + 17 * s), fill=INK)
        d.ellipse((cx - 21 * s, cy + 48 * s, cx + 21 * s, cy + 93 * s), fill=(173, 95, 91), outline=INK, width=max(2, int(3 * s)))
    if blush:
        d.ellipse((cx - 133 * s, cy + 38 * s, cx - 83 * s, cy + 66 * s), fill=PINK)
        d.ellipse((cx + 83 * s, cy + 38 * s, cx + 133 * s, cy + 66 * s), fill=PINK)

def phone(im, screenshot, cx, cy, width=370, tilt=0):
    h = int(width * 2.15)
    layer = Image.new('RGBA', (width + 50, h + 50), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    rr(d, (17, 17, width + 33, h + 33), 46, (36, 50, 46), outline=(36, 50, 46), width=3)
    shot = ImageOps.fit(screenshot, (width - 24, h - 28), method=Image.Resampling.LANCZOS)
    mask = Image.new('L', shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, shot.width, shot.height), radius=34, fill=255)
    layer.paste(shot, (37, 31), mask)
    d.rounded_rectangle((width // 2 - 25, 23, width // 2 + 75, 33), 6, fill=INK)
    if tilt:
        layer = layer.rotate(tilt, resample=Image.Resampling.BICUBIC, expand=True)
    im.alpha_composite(layer, (int(cx - layer.width / 2), int(cy - layer.height / 2)))
home = Image.open(HERE / 'screenshots' / 'normal-home.png').convert('RGB')
result = Image.open(HERE / 'screenshots' / 'normal-result.png').convert('RGB')
home = home.crop((0, 50, 1080, 2320))
result = result.crop((0, 50, 1080, 2320))
hero = Image.open(ROOT / 'outputs' / 'MoaAndroid' / 'app' / 'src' / 'main' / 'res' / 'drawable-nodpi' / 'hero_touch.png').convert('RGBA')

ASSETS = HERE / 'assets'
SCRATCH = ROOT / 'work' / 'marketing-rebuild' / 'cache'
SCRATCH.mkdir(parents=True, exist_ok=True)
W, H, FPS = 720, 1280, 30
DURATIONS = [3.5, 3.5, 4.5, 4.5, 3.4, 3.4, 3.7]
STARTS = [sum(DURATIONS[:i]) for i in range(len(DURATIONS))]
TOTAL = sum(DURATIONS)

SPRITES = {}
original_baby = baby


def baby(d, cx, cy, scale=1, smile=False, wave=0, blush=True):
    original_baby(d, cx, cy, scale, smile, wave, blush)
    s = scale
    # A small sage patch pocket and two apricot snaps on the cream romper.
    rr(d, (cx-52*s, cy+241*s, cx+52*s, cy+316*s), 20*s,
              (183, 207, 172), outline=INK, width=max(2, round(3*s)))
    for dx in (-73, 73):
        d.ellipse((cx+(dx-7)*s, cy+210*s, cx+(dx+7)*s, cy+224*s),
                  fill=APRICOT, outline=INK, width=max(1, round(2*s)))


def prepare_sprites():
    for mood in ('confused', 'happy'):
        sheet = Image.open(ASSETS / f'parents-{mood}.png').convert('RGBA')
        for person in (0, 1):
            part = sheet.crop((person * sheet.width // 2, 0,
                               (person + 1) * sheet.width // 2, sheet.height))
            # Alpha only determines the drawing bounds; original pixels are preserved.
            bounds = part.getchannel('A').point(lambda a: 255 if a > 12 else 0).getbbox()
            sprite = part.crop(bounds)
            sprite.thumbnail((310, 480), Image.Resampling.LANCZOS)
            SPRITES[mood, person] = sprite


def parents(im, t, happy=False):
    mood = 'happy' if happy else 'confused'
    for person, cx in ((0, 191), (1, 529)):
        sprite = SPRITES[mood, person]
        phase = person * 0.85
        # Tiny independent breathing/settling motions keep the drawings calm.
        settle = 8 * math.exp(-max(0, t) * 4)
        bob = 2.2 * math.sin(t * 2.0 + phase)
        angle = (0.6 if happy else 0.4) * math.sin(t * 1.5 + phase)
        layer = sprite.rotate(angle, resample=Image.Resampling.BICUBIC, expand=True)
        bottom = (959 if happy else 929) + bob + settle
        im.alpha_composite(layer, (round(cx - layer.width / 2), round(bottom - layer.height)))


def scene(index, t):
    im = base()
    d = ImageDraw.Draw(im)
    if index == 0:
        text(d, (360, 110), '오늘도 들려오는', 36, MUTED)
        text(d, (360, 192), '아기만의 언어', 62, INK, True)
        baby(d, 360, 568 + math.sin(t * 5) * 7, .92, smile=False, wave=math.sin(t * 7))
        text(d, (360, 1040), '으아… 아부!', 62, INK, True)
        text(d, (360, 1170), '응애, 옹알옹알', 36, MUTED, True)
    elif index == 1:
        text(d, (360, 133), '무슨 뜻일까?', 62, INK, True)
        text(d, (360, 216), '엄마·아빠 머릿속은 물음표', 33, MUTED)
        parents(im, t)
        d = ImageDraw.Draw(im)
        for x, y in [(99, 360), (342, 306), (590, 370)]:
            text(d, (x, y + math.sin(t * 4 + x) * 10), '?', 106, APRICOT, True)
        text(d, (360, 1040), '울음일까, 놀자는 걸까?', 43, INK, True)
    elif index == 2:
        text(d, (360, 120), '그럴 땐,', 42, MUTED)
        text(d, (360, 206), '아기말을 들어봐요', 57, INK, True)
        phone(im, home, 360, 680 + math.sin(t * 2) * 5, 365, -2)
        d = ImageDraw.Draw(im)
        circle = 25 + math.sin(t * 6) * 8
        d.ellipse((459-circle, 638-circle, 459+circle, 638+circle), outline=APRICOT, width=7)
        text(d, (360, 1180), '10초 소리 또는 실시간 화면', 34, INK, True)
    elif index == 3:
        text(d, (360, 120), '아이 시점의 한마디', 59, INK, True)
        phone(im, result, 360, 587, 300, 2)
        d = ImageDraw.Draw(im)
        text(d, (360, 1010), '“나도 같이 이야기하는 중이야.', 39, INK, True)
        text(d, (360, 1072), '한 번만 더 해 줘!”', 46, INK, True)
        text(d, (360, 1187), '가능한 표현 예시 · 보호자가 확인', 28, MUTED)
    elif index == 4:
        text(d, (360, 145), '아!', 110, INK, True)
        for x, y, r, color in [(130, 295, 45, APRICOT), (600, 310, 54, APRICOT), (360, 348, 24, SAGE)]:
            sparkle(d, x, y, r * (1 + .06 * math.sin(t * 4)), color)
        parents(im, t, happy=True)
        d = ImageDraw.Draw(im)
        text(d, (360, 1090), '함께 놀고 싶었구나!', 48, INK, True)
    elif index == 5:
        text(d, (360, 140), '반응해 주는 그 순간', 53, INK, True)
        baby(d, 360, 571 + math.sin(t * 5) * 6, .93, smile=True, wave=math.sin(t * 8))
        for x, y, r in [(110, 357, 18), (601, 415, 16), (564, 285, 11)]:
            d.ellipse((x-r, y-r, x+r, y+r), fill=APRICOT)
        text(d, (360, 1060), '아기가 빵실 웃었어요', 46, INK, True)
        text(d, (360, 1185), '오늘의 작은 대화가 시작돼요', 32, MUTED)
    else:
        d.ellipse((112, 260, 608, 760), fill=PALE)
        art = hero.copy()
        art.thumbnail((560, 560), Image.Resampling.LANCZOS)
        im.alpha_composite(art, ((720-art.width)//2, int(225+(500-art.height)/2)))
        d = ImageDraw.Draw(im)
        sparkle(d, 110, 315, 20)
        sparkle(d, 601, 660, 25, SAGE)
        text(d, (360, 825), '지금 내\n기분이 그래', 72, INK, True)
        text(d, (360, 990), '작은 표현을 더 가깝게', 40, MUTED)
        text(d, (360, 1190), '가능한 표현 예시 · 의도 판정 아님', 24, MUTED)
    return im


def frame_at(tm):
    i = max(i for i, start in enumerate(STARTS) if start <= tm)
    local = tm - STARTS[i]
    frame = scene(i, local)
    fade = .32
    if local < fade and i > 0:
        frame = Image.blend(scene(i-1, DURATIONS[i-1]), frame, local/fade)
    return frame.convert('RGB')


def render(preview=False):
    prepare_sprites()
    for at, name in [(4.7, 'confused'), (17.2, 'happy'), (12.5, 'expression'), (25.0, 'ending')]:
        frame_at(at).save(SCRATCH / f'{name}-frame.png')
    if preview:
        return
    ffmpeg = shutil.which('ffmpeg')
    silent = SCRATCH / 'trailer-silent.mp4'
    proc = subprocess.Popen([ffmpeg, '-y', '-loglevel', 'error', '-f', 'rawvideo',
        '-pix_fmt', 'rgb24', '-s', f'{W}x{H}', '-r', str(FPS), '-i', '-', '-an',
        '-c:v', 'libx264', '-preset', 'fast', '-crf', '18', '-pix_fmt', 'yuv420p', str(silent)], stdin=subprocess.PIPE)
    try:
        for n in range(round(TOTAL * FPS)):
            proc.stdin.write(frame_at(n / FPS).tobytes())
            if n % 180 == 0:
                print(f'{n}/{round(TOTAL * FPS)} frames', flush=True)
    finally:
        proc.stdin.close()
    if proc.wait() != 0:
        raise RuntimeError('Video render failed')
    destination = OUT / 'jigeum-trailer-b-family.mp4'
    from mix_marketing_audio import mix_audio
    mix_audio(silent, destination)
    print(destination, flush=True)



def promo(size, kind):
    pw, ph = size
    im = Image.new('RGBA', size, CREAM + (255,))
    d = ImageDraw.Draw(im)
    if kind == 'thumbnail':
        d.ellipse((-190, -300, 540, 430), fill=PALE)
        d.ellipse((960, 380, 1450, 870), fill=(255, 238, 223))
        text(d, (70, 94), '아기의 옹알이,', 58, INK, True, anchor='la')
        text(d, (70, 181), '무슨 말일까?', 67, INK, True, anchor='la')
        text(d, (70, 296), '10초 소리에서 시작하는 한마디', 28, MUTED, anchor='la')
        baby(d, 250, 476, 0.53, smile=True)
        phone(im, result, 945, 381, 262, -7)
        d = ImageDraw.Draw(im)
        rr(d, (58, 601, 358, 665), 28, INK)
        text(d, (208, 633), '지금 내 기분이 그래', 24, WHITE, True)
    elif kind == 'store':
        d.rectangle((0, 0, pw, ph), fill=INK)
        d.ellipse((-180, -160, 500, 600), fill=(51, 73, 65))
        text(d, (68, 86), '아기말이', 56, WHITE, True, anchor='la')
        text(d, (68, 166), '한 문장이 되는 순간', 61, WHITE, True, anchor='la')
        text(d, (68, 298), '짧은 소리 · 영상 · 보호자 확인', 29, (215, 231, 210), anchor='la')
        baby(d, 312, 427, 0.38, smile=True)
        phone(im, home, 904, 320, 247, 5)
        d = ImageDraw.Draw(im)
        rr(d, (68, 519, 435, 583), 25, APRICOT)
        text(d, (251, 550), '지금 내 기분이 그래', 25, INK, True)
    else:
        d.ellipse((-200, -300, 780, 760), fill=PALE)
        text(d, (75, 110), '이 소리,', 66, INK, True, anchor='la')
        text(d, (75, 207), '무슨 말일까?', 77, INK, True, anchor='la')
        text(d, (75, 326), '아기의 작은 표현을', 40, MUTED, anchor='la')
        text(d, (75, 388), '따뜻한 한마디로', 40, MUTED, anchor='la')
        baby(d, 252, 867, 0.65, smile=True)
        phone(im, result, 794, 1031, 395, -5)
        d = ImageDraw.Draw(im)
        rr(d, (75, 1600, 1005, 1744), 42, INK)
        text(d, (540, 1670), '지금 내 기분이 그래 · 아기말 보기', 39, WHITE, True)
        text(d, (540, 1830), '표현 예시 · 실제 의도 판독 아님', 27, MUTED)
    return im.convert('RGB')

if __name__ == '__main__':
    render()
    promo((1280,720),'thumbnail').save(OUT/'jigeum-trailer-thumbnail.png')
    promo((1200,628),'store').save(OUT/'jigeum-store-feature.png')
    promo((1080,1920),'social').save(OUT/'jigeum-social-portrait.png')
