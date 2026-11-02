"""Frame unaltered release screenshots with Korean captions; no generated UI.

Screens retain real UI pixels; two labelled detail crops improve legibility.
Raw source captures and SHA256 hashes remain available for comparison.
"""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROWS = [
    ('store-01-home.png', '01-home.png', '오늘의 아가말,', '소중한 기록으로', '찍거나 불러오고, 소리부터 살펴봐요.'),
    ('store-03-expression.png', '02-expression.png', '더 풍부해진', '우리 아이의 한마디', '소리 단서를 참고해 상상한 아가말이에요.'),
    ('store-04-record.png', '03-record.png', '그때의 소리까지', '영상카드로 간직해요', '저장한 장면을 다시 보고 나눠요.'),
    ('code-entry-empty.png', '04-code.png', '초대·이벤트 코드,', '앱에서 간편하게', '전체 이용권에서 받은 코드를 등록해요.'),
]
BG = '#FFF8EF'
INK = '#303B36'
MUTED = '#69716C'
ACCENT = '#D8664A'


def font(size, bold=False):
    return ImageFont.truetype('C:/Windows/Fonts/malgunbd.ttf' if bold else 'C:/Windows/Fonts/malgun.ttf', size)


def text(draw, xy, value, size, color=INK, bold=False):
    draw.text(xy, value, font=font(size, bold), fill=color)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--captures', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--code-capture', type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    raw = args.out / 'raw'
    raw.mkdir(exist_ok=True)
    capture = json.loads((args.captures/'store-capture-result.json').read_text(encoding='utf-8'))
    assert capture['status'] == 'pass' and capture['provenance']['verified']
    manifest = {'version': capture['provenance']['version_name'],
                'version_code': capture['provenance']['version_code'], 'real_app_ui': True,
                'generated_ui': False, 'private_baby_media': False,
                'screens': [], 'status': 'local_assets_not_publication'}
    thumbs = []
    for source, name, line1, line2, subtitle in ROWS:
        blob = (args.code_capture if source == 'code-entry-empty.png' else args.captures/source).read_bytes()
        (raw/source).write_bytes(blob)
        screen = Image.open(raw/source).convert('RGB')
        crop = {'store-03-expression.png': (0, 925, 1080, 1825),
                'code-entry-empty.png': (64, 418, 1016, 1265)}.get(source)
        if crop: screen = screen.crop(crop)
        canvas = Image.new('RGB', (1080, 1920), BG)
        draw = ImageDraw.Draw(canvas)
        text(draw, (84, 48), '아가톡  /  Yeolmae_STUDIO', 27, MUTED)
        text(draw, (82, 113), line1, 64, bold=True)
        text(draw, (82, 198), line2, 64, bold=True)
        text(draw, (84, 303), subtitle, 31, MUTED)
        screen.thumbnail((912, 1489) if crop else (670, 1489), Image.Resampling.LANCZOS)
        sx = (1080-screen.width)//2
        sy = 600 if crop else 350
        if crop: text(draw, (84, 480), '실제 앱 화면 일부를 크게 보여드려요', 27, MUTED)
        draw.rounded_rectangle((sx-5, sy-5, sx+screen.width+5, sy+screen.height+5), radius=14, fill='#D8DCD4')
        canvas.paste(screen, (sx, sy))
        text(draw, (84, 1855), '실제 앱 화면 · 코드 값은 표시하지 않았어요' if source == 'code-entry-empty.png'
             else '실제 앱 화면 · 예시 소리로 만든 기록', 25, MUTED)
        canvas.save(args.out/name, optimize=True)
        thumb = canvas.copy(); thumb.thumbnail((270,480), Image.Resampling.LANCZOS); thumbs.append(thumb)
        manifest['screens'].append({'file': name, 'source': 'raw/'+source,
                                   'raw_sha256': hashlib.sha256(blob).hexdigest(),
                                   'sha256': hashlib.sha256((args.out/name).read_bytes()).hexdigest(),
                                   'dimensions': [1080,1920], 'source_crop_px': crop})
    contact = Image.new('RGB', (270*len(thumbs),480), BG)
    for i, thumb in enumerate(thumbs): contact.paste(thumb, (270*i,0))
    contact.save(args.out/'preview.jpg', quality=92)
    graphic = Image.new('RGB',(1024,500),BG)
    draw=ImageDraw.Draw(graphic)
    text(draw,(46,32),'아가톡',34,ACCENT,True)
    text(draw,(44,113),'오늘의 아가말,',45,bold=True)
    text(draw,(44,178),'영상카드로 간직해요',45,bold=True)
    text(draw,(46,272),'소리 단서를 참고해 상상한 한마디',23,MUTED)
    text(draw,(46,316),'우리 아이 자랑에서 함께 나눠요',23,MUTED)
    text(draw,(46,430),'Yeolmae_STUDIO',20,MUTED)
    for file,x in [('store-01-home.png',588),('store-04-record.png',794)]:
        shot=Image.open(raw/file).convert('RGB'); shot.thumbnail((190,423),Image.Resampling.LANCZOS)
        graphic.paste(shot,(x,38))
    graphic.save(args.out/'feature-graphic-1024x500.png',optimize=True)
    manifest['feature_graphic']={'source':'actual home and record screenshots','dimensions':[1024,500]}
    (args.out/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(manifest,ensure_ascii=False))


if __name__ == '__main__':
    main()
