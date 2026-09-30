import sys,json,time,traceback
from pathlib import Path
import run_qa as r
q=r.q

def choose_file(name):
    roots=next((n for n in q.nodes() if n['desc']=='Show roots'),None)
    if roots:
        q.tap(*roots['center']);q.wait_ui('Downloads')
        q.tap(*[n for n in q.nodes() if n['text']=='Downloads'][-1]['center'])
    listing=next((n for n in q.nodes() if n['desc']=='List view'),None)
    if listing:q.tap(*listing['center'])
    q.tap_text(name,attempts=10)
def guide():
    r.home();q.tap_text('돌봄',scroll=False);q.tap_text('백업 파일 가져오기')
def main():
    r.home();before=r.events();q.tap_text('돌봄',scroll=False);q.tap_text('전체 기록 백업하기')
    q.wait_ui('SAVE',seconds=15)
    entries=q.nodes()
    name=next(n for n in entries if '아기말-백업-' in n['text'])
    q.tap(*name['center']);q.shell('input','keycombination','113','29')
    q.shell('input','text','jigeum-qa-backup.json');q.shell('input','keyevent','KEYCODE_BACK')
    # Activity remains in the background while DocumentsUI owns the save destination.
    q.kill_background_process()
    q.tap_text('SAVE',scroll=False);q.wait_ui('기록 백업을 저장했어요',seconds=20)
    q.trace('backup-after-process-death')
    raw=q.adb('exec-out','cat','/sdcard/Download/jigeum-qa-backup.json').stdout
    backup=json.loads(raw);assert len(backup['records'])==len(before)
    (q.RUN/'exported-backup.json').write_bytes(raw)
    guide();choose_file('jigeum-qa-backup.json');q.wait_ui('기록을 합칠까요?')
    q.trace('import-preview-duplicates');q.screenshot('import-preview')
    q.tap_text('기록 합치기');q.wait_ui('백업의 기록을 합쳤어요')
    assert r.events()==before,'Repeat import changed records'
    q.trace('import-deduplicated')
    guide();choose_file('jigeum-qa-broken.json');q.wait_ui('백업 파일 형식이나 크기가 올바르지 않아요',seconds=20)
    assert r.events()==before;q.trace('malformed-import-safe')
    guide();choose_file('jigeum-qa-future.json');q.wait_ui('기록을 합칠까요?')
    q.tap_text('기록 합치기');q.wait_ui('백업의 기록을 합쳤어요')
    had_future=any(row['id']==253402300799999 for row in before)
    assert len(r.events())==len(before)+(0 if had_future else 1)
    start=int(time.time()*1000);r.manual();q.tap_text('이 문장 저장하기');q.wait_ui('우리가 나눈 아기말')
    actual=r.events();new=[a for a in actual if start<=a['id']<=int(time.time()*1000)+1000]
    assert len(new)==1,'Future imported timestamp poisoned current saves'
    q.trace('save-after-future-import',{'new_record_id':new[0]['id']})
if __name__=='__main__':
    try:main();result={'mode':'transfer-e2e','status':'pass'}
    except Exception as e:
        traceback.print_exc();result={'mode':'transfer-e2e','status':'fail','error':str(e)}
        try:q.trace('transfer-e2e-failure',str(e));q.screenshot('transfer-e2e-failure')
        except Exception:pass
    with (q.RUN/'results.jsonl').open('a',encoding='utf-8') as f:f.write(json.dumps(result,ensure_ascii=False)+'\n')
    print(json.dumps(result,ensure_ascii=False),flush=True)
    sys.exit(0 if result['status']=='pass' else 1)
