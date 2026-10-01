from __future__ import annotations
import json, sys, time, traceback, sqlite3, re
from contextlib import closing
from pathlib import Path
from qa_config import ROOT, evidence_dir
import qa_harness as q
import legacy_ui as previous
q.RUN = evidence_dir()
previous.q = q


def choose_video(name):
    home(); q.tap_text('저장된 영상 가져오기'); q.tap_text('영상 고르기')
    time.sleep(.7)
    for _ in range(3):
        roots=next((n for n in q.nodes() if n['desc']=='Show roots'),None)
        if roots: break
        time.sleep(.5)
    if roots:
        q.tap(*roots['center']); q.wait_ui('Downloads')
        # DocumentsUI exposes occluded background nodes before the open drawer.
        downloads=[n for n in q.nodes() if n['text']=='Downloads']
        q.tap(*downloads[-1]['center'])
    q.wait_ui('Files in Downloads')
    listing=next((n for n in q.nodes() if n['desc']=='List view'),None)
    if listing:q.tap(*listing['center'])
    q.tap_text(name,attempts=10)
previous.choose=choose_video

def visible(text): return any(text in n['text'] for n in q.nodes())
def home():
    q.shell('input','keyevent','KEYCODE_BACK'); q.launch()
def events():
    path=q.RUN/'snapshot.db'
    path.write_bytes(q.adb('exec-out','run-as',q.PACKAGE,'cat','databases/moa_observations.db').stdout)
    with closing(sqlite3.connect(path)) as db:
        db.row_factory=sqlite3.Row
        return [dict(row) for row in db.execute('select * from observations order by id desc')]
def manual():
    home(); q.tap_text('소리로 번역하기'); q.tap_text('녹음 없이 직접 번역')
    q.tap_text('옹알이'); q.tap_text('놀이 중'); q.tap_text('아기말 문장 보기')
    q.wait_ui('가능한 한마디')
def migration():
    import xml.etree.ElementTree as ET
    if not (q.RUN/'legacy-before.xml').is_file():
        raise RuntimeError('Migration needs capture_legacy.py on a populated baseline install before upgrading; see README.')
    old=json.loads(next(n.text for n in ET.parse(q.RUN/'legacy-before.xml').getroot() if n.attrib.get('name')=='events'))
    rows=events()
    for expected in old:
        actual=next((r for r in rows if r['id']==expected['id']),None)
        assert actual and all(actual[k]==expected[k] for k in ['sound','context','note','phrase','source']), (actual,expected)
    q.trace('030-migration-preserved',{'legacy_records':len(old)})
def normal():
    manual(); q.screenshot('normal-result')
    q.tap_text('내가 직접 고치기'); q.tap_text('우리 아이 말로 고치기',exact=False)
    q.shell('input','keycombination','113','29'); q.shell('input','keyevent','KEYCODE_DEL')
    q.shell('input','text','QA%sfamily%smoment'); q.shell('input','keyevent','KEYCODE_BACK')
    q.wait_ui('QA family moment')
    q.shell('input','keyevent','KEYCODE_HOME'); time.sleep(1)
    q.kill_background_process()
    q.shell('am','start','-n',q.PACKAGE+'/.MainActivity'); q.wait_ui('QA family moment')
    q.trace('draft-recreated')
    before={row['id'] for row in events()}
    target=q.tap_text('이 문장 저장하기'); x,y=target['center']
    q.shell('input','tap',str(x),str(y)); q.shell('input','tap',str(x),str(y))
    q.wait_ui('우리가 나눈 아기말'); saved=events()
    added=[row for row in saved if row['id'] not in before]
    assert len(saved)==len(before)+1 and len(added)==1 and added[0]['phrase']=='QA family moment',saved
    q.screenshot('saved-record')
    q.tap_text('문장 카드 공유'); time.sleep(1); q.trace('free-share'); q.screenshot('free-share')
    assert any('공유' in n['text'] or 'Sharing' in n['text'] or 'Quick Share' in n['text'] for n in q.nodes())
    q.shell('input','keyevent','KEYCODE_BACK'); home(); q.tap_text('기록',scroll=False)
    q.wait_ui('QA family moment')
    q.trace('normal-complete')
def camera():
    q.shell('pm','grant',q.PACKAGE,'android.permission.CAMERA')
    home(); q.tap_text('카메라로 살펴보기'); q.tap_text('카메라 시작'); q.wait_ui('후면 카메라')
    for i in range(4):
        q.tap_text('전후면 전환'); q.wait_ui('전면 카메라' if i%2==0 else '후면 카메라')
    q.screenshot('camera'); q.tap_text('이 장면으로 문장 만들기'); q.tap_text('놀이 중')
    q.wait_ui('가능한 한마디'); q.trace('camera-context')
    home(); q.tap_text('카메라로 살펴보기'); q.tap_text('카메라 시작'); q.wait_ui('후면 카메라')
    q.tap_text('관찰 끝내기'); q.wait_ui('카메라가 꺼져 있어요'); q.trace('camera-stop')
def album():
    home(); q.tap_text('아기말북',scroll=False); q.wait_ui('한 달의 말')
    q.screenshot('album'); q.tap_text('살구빛 기억'); q.tap_text('보호자 메모도 담기')
    q.tap_text('전체 이용권 보기'); q.wait_ui('닫기 · 원래 화면으로')
    q.trace('billing-unavailable'); q.screenshot('paywall')
    assert not any('₩' in n['text'] for n in q.nodes()),'Unavailable price must not be invented'
    q.tap_text('구매 복원',exact=False); time.sleep(.5); q.trace('restore-unavailable')
    q.shell('input','keyevent','KEYCODE_BACK'); q.wait_ui('표지의 색을 골라요')
    import xml.etree.ElementTree as ET
    tree=ET.fromstring(q.adb('exec-out','cat','/sdcard/moa-ultraqa.xml').stdout)
    for label in ['보호자 메모도 담기','살구빛 기억']:
        assert any(n.attrib.get('checked')=='true' and any(c.attrib.get('text')==label for c in n.iter('node')) for n in tree.iter('node')),f'Choice lost on paywall return: {label}'
    q.trace('album-return')
def transfer():
    home(); q.tap_text('돌봄',scroll=False); q.tap_text('전체 기록 백업하기')
    time.sleep(.6); q.trace('backup-picker'); q.screenshot('backup-picker')
    q.shell('input','keyevent','KEYCODE_BACK'); q.wait_ui('전체 기록 백업하기')
    q.tap_text('백업 파일 가져오기'); time.sleep(.5); q.shell('input','keyevent','KEYCODE_BACK')
    q.wait_ui('전체 기록 백업하기'); q.trace('picker-cancel-recovery')
def layout():
    display=q.display_settings()
    original=q.shell('settings','get','system','font_scale').stdout.decode().strip()
    try:
        q.shell('settings','put','system','font_scale','2.0')
        manual(); q.screenshot('font200-result'); q.tap_text('이 문장 저장하기',scroll=False)
        q.wait_ui('우리가 나눈 아기말'); q.screenshot('font200-timeline'); q.trace('font200-save')
        q.shell('settings','put','system','font_scale','1.0')
        q.shell('wm','size','720x1280'); q.shell('wm','density','320')
        manual(); q.screenshot('small-result'); q.tap_text('이 문장 저장하기',scroll=False)
        q.wait_ui('우리가 나눈 아기말'); q.trace('small-save')
        q.shell('wm','size','1280x720'); q.shell('wm','density','160')
        home(); q.screenshot('wide-home'); q.tap_text('아기말북',scroll=False)
        q.screenshot('wide-album'); q.trace('wide-navigation')
    finally:
        q.restore_display(display)
        if original == 'null': q.shell('settings','delete','system','font_scale')
        else: q.shell('settings','put','system','font_scale',original)
def stress():
    home(); begin=time.monotonic()
    for _ in range(10):
        q.tap_text('소리로 번역하기'); q.wait_ui('녹음 없이 직접 번역')
        q.shell('input','keyevent','KEYCODE_BACK'); q.wait_ui('지금 아기는')
    q.trace('stress-ten-routes',{'seconds':round(time.monotonic()-begin,2)})
    (q.RUN/'stress-meminfo.txt').write_bytes(q.shell('dumpsys','meminfo',q.PACKAGE).stdout)

def access():
    """0.4 unconfigured/free build: gates precede sensors and preserve safe returns."""
    q.shell('pm','revoke',q.PACKAGE,'android.permission.CAMERA',check=False)
    for entry,tag,title in [('카메라로 살펴보기','camera','지금의 표정도 함께 살펴봐요'),
                            ('저장된 영상 가져오기','video','담아 둔 영상으로 이야기를 나눠요')]:
        home(); q.tap_text(entry,exact=False); q.wait_ui(title)
        texts=[n['text'] for n in q.nodes() if n['text']]
        assert not any(value in texts for value in ['카메라 시작','영상 고르기','Files in Downloads','While using the app']),texts
        assert not any('₩' in text for text in texts),'No invented price in an unconfigured build'
        q.trace('free-'+tag+'-gate'); q.screenshot('access-'+tag)
        if tag=='camera':
            q.shell('input','keyevent','KEYCODE_HOME'); time.sleep(1)
            q.kill_background_process()
            q.shell('am','start','-n',q.PACKAGE+'/.MainActivity'); q.wait_ui(title)
            q.trace('paywall-process-restored')
        q.shell('input','keyevent','KEYCODE_BACK'); q.wait_ui('지금 아기는')
    permissions=q.shell('dumpsys','package',q.PACKAGE).stdout.decode('utf-8','replace')
    assert re.search(r'android.permission.CAMERA: granted=false',permissions),permissions[-2000:]
    q.trace('free-media-permission-stays-denied')
    manual(); q.tap_text('이 문장 저장하기'); q.wait_ui('우리가 나눈 아기말')
    q.trace('free-creation-after-gates')

def paywall_layout():
    display=q.display_settings()
    original=q.shell('settings','get','system','font_scale').stdout.decode().strip()
    try:
        for label,size,density,font in [('small','720x1280','320','1.0'),('large-font','1080x2400','420','2.0'),('wide','1280x720','160','1.0')]:
            q.shell('wm','size',size); q.shell('wm','density',density)
            q.shell('settings','put','system','font_scale',font)
            home(); q.tap_text('카메라로 살펴보기',exact=False); q.wait_ui('지금의 표정도 함께 살펴봐요')
            q.screenshot('paywall-'+label)
            q.tap_text('구매 복원',exact=False); q.trace('paywall-'+label+'-restore')
            q.screenshot('paywall-'+label+'-actions')
            q.shell('input','keyevent','KEYCODE_BACK'); q.wait_ui('지금 아기는')
    finally:
        q.restore_display(display)
        if original=='null': q.shell('settings','delete','system','font_scale')
        else: q.shell('settings','put','system','font_scale',original)
def confirm_layout():
    display=q.display_settings()
    try:
        q.shell('wm','size','1280x720'); q.shell('wm','density','160')
        home(); q.tap_text('아기말북',scroll=False); q.screenshot('wide-album-confirmed')
        q.trace('wide-safe-area-confirmed')
    finally:
        q.restore_display(display)

def review_layout():
    """Review entry remains visible, disabled when unconfigured, and returns safely at each size."""
    display=q.display_settings()
    original=q.shell('settings','get','system','font_scale').stdout.decode().strip()
    try:
        for label,size,density,font in [('small','720x1280','320','1.0'),('large-font','1080x2400','420','2.0'),('wide','1280x720','160','1.0')]:
            q.shell('wm','size',size); q.shell('wm','density',density)
            q.shell('settings','put','system','font_scale',font)
            home(); q.tap_text('카메라로 살펴보기',exact=False)
            q.wait_ui('지금의 표정도 함께 살펴봐요')
            q.tap_text('Play 심사 접근',attempts=9)
            q.tap_text('심사 접근 확인',attempts=9)
            q.wait_ui('심사 접근 서비스가 아직 설정되지 않았어요.')
            entries=q.trace('review-'+label+'-disabled')
            assert not any('심사 접근 확인 중' in n['text'] or '카메라 시작' in n['text'] for n in entries)
            q.screenshot('review-'+label+'-actions')
            q.shell('input','keyevent','KEYCODE_BACK'); q.wait_ui('지금 아기는')
        q.trace('review-layout-safe-return')
    finally:
        q.restore_display(display)
        if original=='null': q.shell('settings','delete','system','font_scale')
        else: q.shell('settings','put','system','font_scale',original)

MODES={'migration':migration,'normal':normal,'permission':previous.permission,'camera':camera,'video':previous.video,
       'invalid':previous.invalid,'album':album,'transfer':transfer,'layout':layout,'stress':stress,'confirm-layout':confirm_layout,
       'access':access,'paywall-layout':paywall_layout,'review-layout':review_layout}
if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser(description='Run one UI scenario on the explicitly selected disposable test device.')
    parser.add_argument('mode',choices=sorted(MODES))
    mode=parser.parse_args().mode; start=time.monotonic()
    try:
        MODES[mode](); result={'mode':mode,'status':'pass','seconds':round(time.monotonic()-start,1)}
    except Exception as e:
        result={'mode':mode,'status':'fail','error':str(e)}
        try: q.trace('failure-'+mode,str(e)); q.screenshot('failure-'+mode)
        except Exception: pass
        traceback.print_exc()
    with (q.RUN/'results.jsonl').open('a',encoding='utf-8') as f: f.write(json.dumps(result,ensure_ascii=False)+'\n')
    print(json.dumps(result,ensure_ascii=False),flush=True)
    sys.exit(0 if result['status']=='pass' else 1)
