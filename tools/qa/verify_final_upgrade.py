"""Check final signed upgrade against the already-created baseline record/media."""
import argparse
import hashlib
import json
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'outputs/QA'))
import qa_harness as q
import capture_ui as flow
from signed_media_upgrade import export_backup, verify_legacy_record
from closed_test_ui import download_media
from instrument import installed_provenance
from qa_config import adb_command


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--apk',type=Path,required=True)
    p.add_argument('--baseline-result',type=Path,required=True)
    p.add_argument('--version-code',type=int,required=True)
    args=p.parse_args()
    old=json.loads(args.baseline_result.read_text(encoding='utf-8'))
    assert old['status']=='pass'
    owned=[]
    result={'status':'running','actual_play_install_tested':False,'app_data_cleared':False}
    try:
        proof=installed_provenance(adb_command(),q.PACKAGE,hashlib.sha256(args.apk.read_bytes()).hexdigest())
        assert proof['verified'] and proof['version_code']==args.version_code
        result['installed']=proof
        flow.launch(); q.wait_ui('무료 · 1분 촬영 · 10초 분석')
        q.tap_text('기록'); q.tap_text('장면 보기 · 내려받기'); q.wait_ui('그때의 아기말')
        result['media']=download_media(owned,'final-upgrade',old['old_media']['name'])
        assert result['media']['sha256']==old['old_media']['sha256']
        result['legacy_record']=verify_legacy_record(export_backup(owned),old['old_legacy_record'])
        result.update(status='pass',media_bytes_preserved=True,legacy_text_preserved=True,release_free_limits=True)
    finally:
        if owned: q.shell('rm','-f',*owned)
        result['owned_external_files_removed']=all(q.shell('test','-e',p,check=False).returncode!=0 for p in owned)
        if result['status']=='running': result['status']='fail'
        (q.RUN/'final-upgrade-result.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False))


if __name__=='__main__': main()
