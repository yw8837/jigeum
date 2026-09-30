"""Media/permission scenarios retained from the earlier QA suite; picker overridden by run_qa."""
import time
import qa_harness as q

def visible(text):
    return any(text in n['text'] for n in q.nodes())

def home():
    # Dismiss an external system picker before resetting the owned app task.
    q.shell('input','keyevent','KEYCODE_BACK')
    q.launch()

def permission():
    q.shell('pm','revoke',q.PACKAGE,'android.permission.RECORD_AUDIO',check=False)
    home(); q.tap_text('소리로 번역하기'); q.tap_text('녹음 시작')
    if visible('Don’t allow'): q.tap_text('Don’t allow',scroll=False)
    elif visible("Don't allow"): q.tap_text("Don't allow",scroll=False)
    q.wait_ui('마이크 권한이 있어야'); q.trace('microphone-denied')
    q.tap_text('녹음 없이 직접 번역'); q.wait_ui('들은 것')
    q.shell('pm','grant',q.PACKAGE,'android.permission.RECORD_AUDIO')
    q.audio10(); q.tap_text('다시 녹음하기'); q.wait_ui('녹음 시작')
    q.background()

def choose(name):
    home(); q.tap_text('저장된 영상 가져오기'); q.tap_text('영상 고르기')
    roots=next((n for n in q.nodes() if n['desc']=='Show roots'),None)
    if roots:
        q.tap(*roots['center']); q.tap_text('Downloads',scroll=False)
    listing=next((n for n in q.nodes() if n['desc']=='List view'),None)
    if listing: q.tap(*listing['center'])
    for _ in range(7):
        if visible(name): break
        q.swipe_up()
    q.tap_text(name,scroll=False)

def video():
    choose('jigeum-qa-fuss.mp4'); q.wait_ui('다른 영상 고르기',seconds=40)
    q.trace('video-audio-observation'); q.screenshot('video-audio')
    q.tap_text('울음'); q.tap_text('이 영상으로 문장 만들기')
    q.tap_text('수유 전'); q.wait_ui('가능한 한마디'); q.trace('video-expression')
    choose('jigeum-qa-no-audio.mp4'); q.wait_ui('다른 영상 고르기',seconds=40)
    q.tap_text('소리 없음'); q.tap_text('이 영상으로 문장 만들기'); q.tap_text('놀이 중')
    q.wait_ui('가능한 한마디'); q.trace('video-without-audio')
    choose('jigeum-qa-silence.mp4'); q.wait_ui('다른 영상 고르기',seconds=40)
    assert visible('거의 무음'),[n['text'] for n in q.nodes() if n['text']]
    q.trace('video-silent-track')

def invalid():
    for name,expect in [('long','재생 시간이 30초 이내인 영상을 골라 주세요.'),
                        ('big','50MB 이하의 크기를 확인할 수 있는 영상을 골라 주세요.'),
                        ('broken','이 영상을 열거나 분석하지 못했어요. 다른 파일을 골라 주세요.')]:
        choose('jigeum-qa-'+name+'.mp4'); q.wait_ui(expect,seconds=30)
        # The picker is available again and the result CTA is absent.
        assert not visible('이 영상으로 문장 만들기')
        q.trace('invalid-'+name)
    choose('jigeum-qa-fuss.mp4')
    q.tap_text('닫기',scroll=False); q.wait_ui('지금 아기는'); time.sleep(1)
    q.tap_text('저장된 영상 가져오기'); q.wait_ui('영상 고르기')
    assert not visible('이 영상으로 문장 만들기')
    q.trace('video-cancel-reentry')
