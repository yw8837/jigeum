"""0.5.5 signed-app media save/play/SAF/share/free access on an owned emulator.

Pass a locally authorized sound fixture. Never redistributes the input. This tests
working media flows, not infant intent accuracy. Evidence contains only QA data.
"""
import argparse
import json
import subprocess
import time
from pathlib import Path
import qa_harness as q
import preview_ui as flow


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--preview-apk',type=Path,required=True)
    p.add_argument('--release-apk',type=Path,required=True)
    p.add_argument('--source',type=Path,required=True)
    args=p.parse_args()
    result={'status':'running','scenarios':[], 'intent_accuracy_tested':False}
    saved_name=None
    display=q.display_settings()
    font=q.shell('settings','get','system','font_scale').stdout.decode().strip()
    try:
        result['preview_install']=flow.install(args.preview_apk)
        q.adb('push',str(args.source),'/sdcard/Download/agattok-media-source.mp4')
        flow.import_video('agattok-media-source.mp4')
        q.tap_text('영상 + 소리로 번역하기')
        q.wait_ui('장면과 함께 저장하기',seconds=65)
        q.screenshot('01-result-with-save'); q.trace('result-with-save')
        q.tap_text('장면과 함께 저장하기')
        q.wait_ui('우리가 나눈 아기말',seconds=125)
        q.screenshot('02-saved-scene'); q.tap_text('장면 보기 · 내려받기')
        q.wait_ui('번역 영상 내려받기')
        q.tap_text('선택 구간 다시 듣기',exact=False)
        q.wait_ui('재생 멈추기',seconds=8)
        q.screenshot('03-playback'); q.trace('saved-player')
        result['scenarios'].append('real model -> default media save -> persistent record -> movie playback')
        q.tap_text('번역 영상 내려받기')
        entries=q.nodes()
        saved_name=next(n['text'] for n in entries if n['text'].endswith('.mp4') and n['text'].startswith('아가톡-'))
        roots=next((n for n in entries if n['desc']=='Show roots'),None)
        if roots:
            q.tap(*roots['center'])
            downloads=[n for n in q.wait_ui('Downloads') if n['text']=='Downloads']
            q.tap(*downloads[-1]['center'])
        entries=q.nodes()
        save=next(n for n in entries if n['text'].lower()=='save' and n['enabled'])
        q.tap(*save['center'])
        q.wait_ui('번역 영상을 선택한 위치에 내려받았어요',seconds=25)
        downloaded=q.RUN/'downloaded-translation.mp4'
        q.adb('pull','/sdcard/Download/'+saved_name,str(downloaded))
        probe=q.cmd(['ffprobe','-v','error','-show_streams','-show_format','-of','json',str(downloaded)])
        metadata=json.loads(probe.stdout)
        assert {s['codec_type'] for s in metadata['streams']} >= {'audio','video'}
        assert 1 <= float(metadata['format']['duration']) <= 30.15
        q.cmd(['ffmpeg','-hide_banner','-loglevel','error','-i',str(downloaded),'-ss','0.5','-frames:v','1',str(q.RUN/'downloaded-frame.png')])
        result['download']={'bytes':downloaded.stat().st_size,'duration':metadata['format']['duration'],'streams':[s['codec_type'] for s in metadata['streams']]}
        result['scenarios'].append('actual Android CreateDocument -> Downloads MP4 with video and original audio')
        q.screenshot('04-downloaded'); q.trace('downloaded')
        q.tap_text('번역 영상 공유')
        activity=q.shell('dumpsys','activity','activities').stdout.decode('utf-8','replace')
        assert 'ChooserActivity' in activity or 'ResolverActivity' in activity
        assert '/exports/dispatched/jigeum-' in activity and '.mp4' in activity
        q.screenshot('05-mp4-share'); q.shell('input','keyevent','KEYCODE_BACK')
        result['scenarios'].append('MP4 system share chooser uses dispatched FileProvider copy')
        # Activity recreation at 200% font must retain the opened saved record.
        q.tap_text('번역 영상 내려받기')
        q.shell('settings','put','system','font_scale','2.0')
        q.shell('input','keyevent','KEYCODE_BACK')
        q.wait_ui('그때의 아기말',seconds=15)
        q.screenshot('06-large-type'); q.trace('large-type')
        q.shell('settings','put','system','font_scale','1.0')
        q.shell('input','keyevent','KEYCODE_BACK')
        flow.flow.launch(); q.tap_text('기록'); q.tap_text('장면 보기 · 내려받기')
        q.wait_ui('그때의 아기말')
        result['scenarios'].append('cold restart and font recreation retain saved media; canceled picker recovers')
        result['release_install']=flow.install(args.release_apk)
        flow.flow.launch(); q.tap_text('기록'); q.tap_text('장면 보기 · 내려받기')
        q.wait_ui('번역 영상 내려받기')
        q.screenshot('07-release-free-media')
        q.tap_text('번역 영상 내려받기'); q.shell('input','keyevent','KEYCODE_BACK')
        q.shell('input','keyevent','KEYCODE_BACK')
        q.tap_text('아기말북'); q.tap_text('전체 이용권 보기')
        q.wait_ui('쌓인 아기말을 한 권으로')
        q.screenshot('08-release-pdf-gated'); q.trace('release-pdf-gated')
        result['scenarios'].append('signed preview->release preserves media; download stays free; new PDF still gated')
        result['status']='pass'
    except Exception as e:
        result.update(status='fail',error=str(e)); q.screenshot('failure'); q.trace('failure')
        raise
    finally:
        q.restore_display(display)
        if font=='null': q.shell('settings','delete','system','font_scale')
        else: q.shell('settings','put','system','font_scale',font)
        # Delete only our known QA source and the exact file created by this run.
        q.shell('rm','-f','/sdcard/Download/agattok-media-source.mp4')
        if saved_name: q.shell('rm','-f','/sdcard/Download/'+saved_name)
        (q.RUN/'media-ui-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
        print(json.dumps(result,ensure_ascii=False))

if __name__=='__main__': main()
