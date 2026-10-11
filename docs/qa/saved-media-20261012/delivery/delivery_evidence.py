from pathlib import Path
import hashlib,json,shutil,subprocess
from datetime import datetime
base=Path(__file__).parent;root=base.parents[1];out=root/'docs/qa/saved-media-20261012/delivery'
out.mkdir(exist_ok=True)
gpu=json.loads((base/'delivery-gpu/diagnosis.json').read_text('utf8'))
smoke=json.loads((base/'signed-smoke-gpu/result.json').read_text('utf8'))
assert gpu['status']=='completed' and len(gpu['rows'])==2 and all(r['verdict']=='pass' for r in gpu['rows'])
assert smoke['status']=='pass' and len(smoke['scenarios'])==2 and smoke['owned_external_files_removed']
assert smoke['beta_media']['sha256']==smoke['release_media']['sha256']
assert 19.8<=float(smoke['beta_media']['duration'])<=20.15
aab=base/'final/Agattok-0.5.24-beta-code38.aab'
assert hashlib.file_digest(aab.open('rb'),'sha256').hexdigest()=='6c3e3eaebb8897c5cf586d3d723432d96c771a227c3ac1009bfe1e20899f9217'
for name in ('delivery-diagnosis','delivery-gpu','signed-smoke-gpu'):
    folder=out/name;folder.mkdir(exist_ok=True)
    for item in (base/name).iterdir():
        if item.suffix in ('.json','.png') or item.name=='ui-steps.jsonl':shutil.copyfile(item,folder/item.name)
for name in ('diagnose_delivery.py','signed_smoke_gpu.py','delivery_evidence.py','delivery_state.py'):
    shutil.copyfile(base/name,out/name)
gpu_log=(base/'delivery-gpu/emulator.out').read_text('utf8',errors='replace')
gpu_lines=[line for line in gpu_log.splitlines() if any(term in line for term in ('Graphics backend','vulkan_mode_selected','Physical GPU','physical GPU','Model: NVIDIA','Boot completed'))]
(out/'gpu-environment.txt').write_text('Launch: C:/MoaSdk/emulator/emulator.exe -avd MoaQA35 -port 5556 -no-window -no-audio -no-snapshot-load -no-snapshot-save -memory 2048 -gpu host\nANDROID_AVD_HOME=C:/MoaAVD\nOnly renderer changed from swiftshader_indirect. No AVD wipe/new AVD.\n\n'+'\n'.join(gpu_lines)+'\n','utf8')
supervisor={'status':'stopped_host_pressure','completed_rows':2,'third_analysis_not_run':True,'reason':'host available memory minimum 1,622,016 bytes after two timeout results; stopped owned runner before redundant third analysis','owned_fixture_removed':True,'owned_emulator_stopped_before_gpu_restart':True}
(out/'delivery-diagnosis/supervisor.json').write_text(json.dumps(supervisor,indent=2),'utf8')
summary_path=root/'docs/qa/saved-media-20261012/summary.json'
summary=json.loads(summary_path.read_text('utf8'))
summary.update(status='local_delivery_gates_pass_not_uploaded',historical_emulator_timeout_root_cause='environment-dependent; software-renderer timeouts preserved; exact causal attribution unresolved',signed20_result='two cold-process quiet runs and signed UI/export gate passed with host GPU',signed_release_relock_pass=True,signed_release_relock_status='pass_same_build',signed20_ui_seconds=None)
summary['delivery_diagnosis']={'software_gpu_verdicts':['fail','fail'],'software_gpu_host_available_min_bytes':[71299072,1622016],'host_gpu_verdicts':['pass','pass'],'host_gpu_host_available_min_bytes':[r['host_available_min'] for r in gpu['rows']],'quiet_checks_are_not_latency_measurements':True,'product_timeout_ms':45000,'product_code_changed_in_diagnosis':False,'same_package_signed20_export_sha256':smoke['beta_media']['sha256'],'same_package_signed20_export_seconds':smoke['beta_media']['duration']}
summary['ultraqa']['followup']='Focused delivery verification passed; prior stopped run and cleanup blocks preserved. Followup cleanup and Play still pending.'
summary_path.write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n','utf8')
heading='''### 후속 배포 검증 — 2026-10-12 signed38 20초/저장/정식 재잠금 통과

사용자가 실제 테스터 제공까지 완료를 요청해 기존 실패를 보존한 후속 검사를 진행했다.
같은 서명38·같은 공개20초 파일·45초 제품 제한을 유지했고, software GPU에서는 quiet/polling
모두 timeout(호스트 여유 RAM 최저68MiB/1.55MiB)이었다. 중복 세번째 분석은 시작 전 중지했다.
동일 AVD의 GPU를 host(NVIDIA RTX3050)로 바꾼 두 프로세스 첫분석은 모두 실제 결과를 생성했다.
RAM 최저값은 서로 달라 GPU/메모리 단독 원인을 확정하지 않는다. 제품코드·모델·범위·제한 변경0.
46초 기다린 뒤 확인한 시간은 실제 분석 소요시간이 아니다.

그 뒤 **같은 signed beta38의 실제20초 결과→장면저장→음성 포함 MP4 내보내기**와
**같은 키 release38 전환→MP4 해시 보존·시각/PDF 재잠금·무료60초촬영/10초분석**을 통과했다.
[후속 근거](qa/saved-media-20261012/delivery/README.md). 기존37→38보존/서명/모델 검사는 그대로 유효하다.
이는 새 실폰 UI 성능 또는 저사양폰/일반 정확도 입증이 아니다. software GPU 실패를 해결된 제품결함으로 기록하지 않는다.
Alpha 제출 대상은 기존 최종 betaAAB SHA `6c3e3eaebb8897c5cf586d3d723432d96c771a227c3ac1009bfe1e20899f9217`.
아직 업로드/검토/제공 전이며 마지막 제공 확인은37이다.

'''
path=root/'docs/HANDOFF.md';content=path.read_text('utf8');first,rest=content.split('\n',1);path.write_text(first+'\n\n'+heading+rest.lstrip('\n'),'utf8')
path=root/'docs/NEXT.md';content=path.read_text('utf8');path.write_text('''# 즉시 다음 단계 — 2026-10-12

새 signed38의 후속20초 UI/장면저장/MP4내보내기/정식재잠금 gate는 host GPU에서 통과했다.
기존 software GPU timeout과 환경 의존성은 실패 원문과 함께 보존했다.
이제 소유 QA기기 정리와 후속 근거 커밋·푸시 후 정확한 betaAAB38을 기존 Alpha로
제출하고 실제 제공 상태까지 확인한다. 이 단계 전에는 테스터 업데이트 제공 완료를 주장하지 않는다.
기존 테스터/다른 트랙/프로덕션/상품/권한을 변경하지 않는다.

---

'''+content,'utf8')
report='''# Signed38 delivery gate follow-up

## Scope and result

Same signed beta38 APK (106507f17a717cbf2cde5339df7faee9bb94b7669013837a927ca3b496cf5dae),
same public20 file (9d57307e1fbd0ff3329e9ed5eba90d6713e619c9ac5d7b65cc6118a5876d8877).
No product code/model/threshold/coverage/45-second deadline changes. No new build.

| Scenario | Actual | Evidence |
|---|---|---|
| Software GPU, no analysis UI polling | App timeout | delivery-diagnosis/diagnosis.json row0 |
| Software GPU, normal UI polling | App timeout | delivery-diagnosis/diagnosis.json row1 |
| Host GPU, two separate process-first analyses | Both result screens | delivery-gpu/diagnosis.json |
| New beta20 analysis → scene save → audio/video MP4 | Pass | signed-smoke-gpu/result.json |
| Same-key release relock and same exported file hash | Pass | signed-smoke-gpu/result.json |

First environment reached68MiB/1.55MiB free host RAM. Aborted redundant third analysis
before start, stopped owned runner/device and removed owned external fixture. See supervisor.json.
Host GPU selected NVIDIA RTX3050. Second environment also had memory pressure; GPU,
CPU scheduling and memory effects are not independently established. Do not label this a
product performance fix or claim exact causal attribution. 46-second quiet waits are
observation scheduling, not app elapsed-time measurements; the app retained its45s limit.
The input is1fps and its earlier S25 evidence is20/PARTIAL visual observations, not100.

## Commands and meaningful bounds

- diagnose_delivery.py: same actual gallery-import/20-second UI path; first software
  run was supervisor-stopped after2 completed failures. Startup ADB environment miss is
  harness-only; explicit path supplied on restart, before any product verdict.
- diagnose_delivery.py delivery-gpu quiet quiet: exit0; each row explicitly pass.
- signed_smoke_gpu.py: exit0 only after analysis, export and release checks pass.
- Existing package/upgrade checks were not needlessly rebuilt or replayed.

Independent Spec review confirmed saved-media changes do not alter20-second inference,
45s timeout or observation cadence; added pause-expression path exits immediately above10s.
User original gallery save successes and S25 direct6.145/5.516sec analysis remain separate
evidence, not signed-beta physical UI performance or emotion/need accuracy.

## Cleanup / publication

External fixtures are removed by drivers; owned emulator app/shutdown and lifecycle
cleanup are recorded separately before upload. User S25 research records remain intact.
Old policy-blocked file deletions remain blocked. Only the existing Alpha beta is authorized.
Upload/review submission is not tester availability; exact version38 available state is required.
'''
(out/'README.md').write_text(report,'utf8')
path=root/'docs/qa/saved-media-20261012.md';content=path.read_text('utf8');path.write_text(content+'\n\n## 후속 배포 gate 검사\n\n'+heading.replace('[후속 근거](qa/','[후속 근거]('),'utf8')
print('Current followup evidence recorded; no Play availability claimed.')
