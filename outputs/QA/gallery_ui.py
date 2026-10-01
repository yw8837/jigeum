"""Signed-app gallery exports on an owned emulator; caller supplies authorized local sound.

Exercises direct album PNG/MP4, editing, record export, small/large type and free access.
Does not establish infant intent accuracy. Source and exported media stay out of Git.
"""
import argparse
import json
import re
from pathlib import Path
import qa_harness as q
import preview_ui as flow


def album_files(kind):
    folder='/sdcard/'+('Movies' if kind=='video' else 'Pictures')+'/아가톡/'
    result=q.shell('ls','-1',folder,check=False).stdout.decode('utf-8','replace')
    names={folder+n for n in result.splitlines() if re.fullmatch(r'아가톡-\d+-[0-9a-f-]{36}\.(mp4|png)',n)}
    return names


def new_copy(kind,before,label,owned):
    after=album_files(kind)
    made=after-before
    assert len(made)==1,(before,after)
    path=made.pop()
    owned.append(path)
    target=q.RUN/(label+('.mp4' if kind=='video' else '.png'))
    q.adb('pull',path,str(target))
    probe=json.loads(q.cmd(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(target)]).stdout)
    streams=probe['streams']
    visual=next(s for s in streams if s['codec_type']=='video')
    rotation=next((s.get('rotation',0) for s in visual.get('side_data_list',[]) if s.get('side_data_type')=='Display Matrix'),0)
    width,height=visual['width'],visual['height']
    if abs(rotation)%180==90:width,height=height,width
    assert width==720,(width,height,rotation)
    assert 960<=height<=4096,(width,height,rotation)
    if kind=='video':
        assert {s['codec_type'] for s in streams}>={'video','audio'}
        assert 1<=float(probe['format']['duration'])<=20.15
        q.cmd(['ffmpeg','-hide_banner','-loglevel','error','-i',str(target),'-ss','0.1','-frames:v','1',str(q.RUN/(label+'-frame.png'))])
    return path,dict(file=target.name,bytes=target.stat().st_size,width=width,height=height,rotation=rotation,
        duration=probe['format'].get('duration'),streams=[s['codec_type'] for s in streams])


def top():
    size=q.shell('wm','size').stdout.decode()
    width,height=map(int,re.findall(r'(\d+)x(\d+)',size)[-1])
    for _ in range(7):q.shell('input','swipe',str(width//2),str(int(height*.25)),str(width//2),str(int(height*.70)),'230')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--preview-apk',type=Path,required=True)
    p.add_argument('--release-apk',type=Path,required=True)
    p.add_argument('--source',type=Path,required=True)
    p.add_argument('--record-only',action='store_true',help='Continue saved-record checks after the same signed APK passed the result/export prefix; retain earlier evidence.')
    args=p.parse_args()
    result=dict(status='running',scenarios=[],copies=[],record_only=args.record_only,intent_accuracy_tested=False)
    display=q.display_settings()
    font=q.shell('settings','get','system','font_scale').stdout.decode().strip()
    owned=[]
    try:
        result['preview_install']=flow.install(args.preview_apk)
        q.shell('wm','size','720x1280');q.shell('wm','density','320');q.shell('settings','put','system','font_scale','1.0')
        if args.record_only:
            flow.flow.launch();q.tap_text('기록');q.wait_ui('QA_GALLERY_EDITED')
        else:
            q.adb('push',str(args.source),'/sdcard/Download/agattok-gallery-source.mp4')
            flow.import_video('agattok-gallery-source.mp4')
            q.tap_text('영상 + 소리로 번역하기')
            q.wait_ui('장면과 함께 저장하기',seconds=65)
            q.screenshot('01-small-result');q.trace('small-result')
            before=album_files('image')
            q.tap_text('카드 이미지 앨범에 저장',attempts=12)
            q.wait_ui('아가톡 앨범에 카드 이미지를 저장했어요',seconds=35)
            path,evidence=new_copy('image',before,'result-card',owned);result['copies'].append(evidence)
            q.screenshot('02-card-saved');q.trace('card-saved')
            # Real Compose text edit; ASCII is used because adb input cannot type Korean IME text.
            q.tap_text('기록에 메모·수정 남기기',attempts=12)
            editor=next(n for n in q.nodes() if '보호자가 남길 문장' in n['text'])
            q.tap(*editor['center']);q.shell('input','keycombination','KEYCODE_CTRL_LEFT','KEYCODE_A')
            q.shell('input','text','QA_GALLERY_EDITED');q.shell('input','keyevent','KEYCODE_BACK')
            top();q.tap_text('기록 수정 접기',attempts=12);top()
            before=album_files('video')
            q.tap_text('영상 카드 앨범에 저장',attempts=14)
            q.wait_ui('아가톡 앨범에 영상 카드를 저장했어요',seconds=125)
            path,evidence=new_copy('video',before,'edited-result-video',owned);result['copies'].append(evidence)
            q.screenshot('03-video-saved');q.trace('video-saved')
            result['scenarios'].append('small signed preview: actual model -> rendered result card PNG -> edited card MP4 with original audio')
            q.tap_text('장면과 함께 저장하기');q.wait_ui('우리가 나눈 아기말',seconds=125);q.wait_ui('QA_GALLERY_EDITED')
        q.tap_text('장면 보기 · 내려받기');q.wait_ui('선택 구간 다시 듣기')
        q.tap_text('선택 구간 다시 듣기',exact=False);q.wait_ui('재생 멈추기',seconds=10)
        q.screenshot('04-record-playback')
        before=album_files('video');q.tap_text('앨범에 영상 저장',attempts=12)
        q.wait_ui('아가톡 앨범에 영상 카드를 저장했어요',seconds=35)
        path,evidence=new_copy('video',before,'record-video',owned);result['copies'].append(evidence)
        q.screenshot('05-record-gallery-message');q.trace('record-gallery-message')
        result['scenarios'].append('saved edited record -> actual MP4 playback -> album export and visible completion inside dialog')
        # Font recreation must preserve the saved record and show completion at 200%.
        q.shell('settings','put','system','font_scale','2.0');top();q.wait_ui('그때의 아기말',seconds=20)
        before=album_files('image');q.tap_text('카드 이미지 앨범에 저장',attempts=16)
        q.wait_ui('아가톡 앨범에 카드 이미지를 저장했어요',seconds=35)
        path,evidence=new_copy('image',before,'record-card-font200',owned);result['copies'].append(evidence)
        q.screenshot('06-font200-completion');q.trace('font200-completion')
        result['scenarios'].append('small display plus font200: preserved saved card, reachable export, completion scrolled into view')
        q.shell('settings','put','system','font_scale','1.0');top();q.wait_ui('그때의 아기말')
        q.shell('input','keyevent','KEYCODE_BACK')
        result['release_install']=flow.install(args.release_apk)
        flow.flow.launch();q.tap_text('기록');q.wait_ui('QA_GALLERY_EDITED');q.tap_text('장면 보기 · 내려받기')
        q.wait_ui('선택 구간 다시 듣기')
        before=album_files('image');q.tap_text('카드 이미지 앨범에 저장',attempts=12)
        q.wait_ui('아가톡 앨범에 카드 이미지를 저장했어요',seconds=35)
        path,evidence=new_copy('image',before,'release-free-card',owned);result['copies'].append(evidence)
        q.screenshot('07-release-free-gallery');q.trace('release-free-gallery')
        q.shell('input','keyevent','KEYCODE_BACK');q.tap_text('아기말북');q.tap_text('전체 이용권 보기')
        q.wait_ui('쌓인 아기말을 한 권으로');q.screenshot('08-release-pdf-gated')
        result['scenarios'].append('signed preview -> release preserves media; gallery copy remains free; new PDF stays gated')
        result['status']='pass'
    except Exception as failure:
        result.update(status='fail',error=str(failure));q.screenshot('failure');q.trace('failure');raise
    finally:
        q.restore_display(display)
        if font=='null':q.shell('settings','delete','system','font_scale')
        else:q.shell('settings','put','system','font_scale',font)
        q.shell('rm','-f','/sdcard/Download/agattok-gallery-source.mp4')
        for path in owned:q.shell('rm','-f',path)
        result['cleanup']=dict(source_removed=True,generated_gallery_files_removed=len(owned),display_restored=True)
        (q.RUN/'gallery-ui-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(result,ensure_ascii=False))


if __name__=='__main__':main()
