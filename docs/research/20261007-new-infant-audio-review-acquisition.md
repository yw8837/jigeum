# 새 영아 source 2개: 취득과 음량 검수 준비

2026-10-07. [사전 metadata 조사](20261007-next-infant-training-source.md)의 Rodcencko9437 / Sauron974244237만 대상이다. 이 문서는 **실행 전 QA 계약**으로 먼저 만들었다. 실제 결과는 실행 후 아래에 추가한다. 메인은 Git/ADB/통합 QA를 담당하며 이 작업은 지정된 코드2개·문서1개·ignored `work/new-infant-review-20261007/`만 소유한다.

## 사전 QA matrix

| ID | 경계·입력 | 기대 | 실행 방법 |
|---|---|---|---|
|Q1|source URL/license/ID/blocked/label 변경|네트워크 전 거부; exact2 source·canonical SHA 고정|고유 unit |
|Q2|범위 밖 output/기존 run/직접 worker/잘못된 FFmpeg|취득 전 실제 CLI 거부; 파일 덮어쓰기 없음|고유 unit + 실제 CLI |
|Q3|HTTP redirect/응답 URL·type 변경/3MiB 초과|거부·실패 기록; 허용 URL 외 요청 없음|네트워크 없는 unit |
|Q4|기존 검증 사본/변조·미기록 사본|같은 경로 사본 재사용; 변조·미기록 사본 거부|고유 unit |
|Q5|whole MP3 ≤120초 → mono16k s16le 메모리|실제 sample duration·format·RMS/peak/rail clipping/DC·1초 bin 완전 기록; PCM 파일0|unit worked examples + 실제2파일 |
|Q6|실패·필수 field 누락·source/code/audio 종료pin 변경|completed 거부; failure/부분 취득/용량 증거 보존|고유 unit |
|Q7|fresh worker·자손·시간/메모리/log 한계|Job180초/512MiB/1MiB·자손0·절단0·전후용량|실제 Job/process/storage JSON |

## 사전 제한

- 공식 설명 페이지를 redirect 없이 재확인한 후, exact CDN2개만 GET한다. 각 MP3 byte cap3MiB, HTTP20초. 원음은 MP3 preview 사본2개만 단일 audio directory에 보존한다. 실패 후 재시도도 검증된 같은 파일을 재사용하고 사본을 복제하지 않는다.
- 이미 설치된 full FFmpeg9.0와 같은 폴더의 ffprobe를 hash/capability로 고정한다. decode는 mono16k signed16 little endian, 최대120초, 메모리만 사용한다.
- 음량은 resample/downmix된 PCM의 측정치다. 아기 존재·사건·감정·잡음 분리의 정답을 뜻하지 않는다. 두 파일 모두 `training_blocked=true`, `approved_training_label=null`, `audio_review_completed=false`다.
- 청취·감정/영아 score 추론·사건 label·학습/calibration·모델 download·private 자료 접근·외부 연락·Git/빌드/ADB·삭제를 수행하지 않는다. 원음 청취 API가 없으면 실제 청취 불가를 명시한다. 이번 결과를 인식 개선이나 앱 업데이트로 보고하지 않는다.

## 실제 결과

**2026-10-07 06:28:25–06:28:42 KST의 run02는 exact2 preview 취득·전체 메모리 decode·음량 통계 기록을 완료했다.** MP3 **2개 / 667,032B**를 `work/new-infant-review-20261007/audio/`에 한 사본씩 보존했다. 재다운로드/중복 MP3/PCM·WAV 파일은0이다. caption-only·사건 정답0·학습 승인0을 유지하며 기존 학습 양성7 clip/4 source/40.7초는 그대로다.

### 실제 취득·전체 decode

| ID·source | official exact preview | MP3 bytes·SHA256 | 실제 PCM sample·duration·bin |
|---|---|---|---|
|9437 / Rodcencko|https://cdn.freesound.org/previews/9/9437_383-lq.mp3|12,240 /61e4354ad089ecd676b2a5d1e9dfde32a4695563087420a8f0e46bb67b1e86c0|21,456 sample /**1.341초** /2 bin |
|244237 / Sauron974|https://cdn.freesound.org/previews/244/244237_152878-lq.mp3|654,792 /05aa0e243efcbb3e66265f2966f9dd279a7d254ccf5193cea7dbcf6dd7349ac9|1,560,295 sample /**97.5184375초** /98 bin |

두 취득 파일은 ffprobe에서 **MP3 /24,000Hz /stereo**로 확인됐다. 원 페이지의 AIFF/WAV 원본을 받은 것이 아니라 lossy CDN preview다. format duration은 각각1.340958/97.518417초이며, whole decoded sample duration과의 차이는0.000042/0.0000205초다. decode는 **mono /16,000Hz /s16le**, 최대120초로 제한했고 두 파일 모두 전체 길이가120초 이하다. 원본 sample rate/bit depth·무손실 음질을 확보한 것으로 표현하지 않는다.

| ID | whole RMS | peak | DC | signed16 rail clipping |1초 bin RMS 범위 |
|---|---:|---:|---:|---:|---:|
|9437|.25038692182304073|.9119873046875|−.00014914015528873041|0 sample /0|.027409549146051343–.28950983438799893 |
|244237|.015819456748275437|.510284423828125|.0000002721223002400988|0 sample /0|0–.0756548341271404 |

수치는 full scale1을 기준으로 downmix/resample된 PCM에 대해 계산했다. rail clipping은 이 PCM에서 signed16 양끝값 −32768/+32767에 닿은 sample count다. **0 clipping은 원본·녹음·MP3 단계의 clipping 부재를 입증하지 않는다.** 마지막 bin은 각각 .341초/.5184375초의 partial bin이다.100개 bin 모두 start/end sample·seconds·sample count·RMS/peak/DC/clipping count/fraction을 기록했고 whole/bin 가중 집계·coverage를 검증했다.244237 마지막 partial bin의 PCM은0이지만 이를 무발성 사건 정답으로 쓰지 않는다. 높은9437 RMS를 영아 확신이나 낮은244237 RMS를 누락 원인으로 해석하지 않는다.

### 현재 공식 권리 재확인

- Rodcencko9437: **06:28:27 KST**, HTML38,206B/SHA `c59fbd4b6cd8b55c33dfff29ba8f102a3e01946027c7ed3293755b2f83002977`.
- Sauron974244237: **06:28:30 KST**, HTML45,807B/SHA `e1bc0d602881941b65d068815e403ada696e8b88521bbed4f96b84af592bad5b`.
- 두 페이지의 exact title/description/license anchor/preview URL을 사전 canonical source와 비교해 일치했고 description 파생 링크0이다. 현재 license는 `http://creativecommons.org/publicdomain/zero/1.0/`다. 두 공식 HTML body를 run02에 보존했다. 동적 HTML hash는 앞선 metadata 조사 때와 다르며, 이것을 음원 변경으로 세지 않는다.
- 월령18/12개월·일상 녹음 여부는 caption이다. 원작자·아동·자연성·실제 발성 구간은 검증하지 않았다. `training_blocked=true`, `approved_training_label=null`, `audio_review_completed=false`, `event_ground_truth=false`를 source·case·완료 JSON에서 검증했다.

### 실패와 수정 이력

`run01`은 **exit1 /2.503044초 /자손0**으로 끝났다. 첫 official HTML을 읽은 뒤 `HTTPResponse`가 EOF에서 `fp`를 닫았는데 다음 read 전에 `response.fp.raw`를 참조해 `AttributeError`가 발생했다. page confirmation·MP3 GET·decode 결과는0이다. [failure.json](../../work/new-infant-review-20261007/run01/failure.json), parent-failure/process/run.log/전후 storage와 당시 source/code pins를 보존했다. 당시 requests 항목의 `started` 상태는 terminal failure.json과 함께 읽으며 성공한 페이지 검증으로 세지 않는다.

EOF에서 닫힌 `fp`를 감지하는 guard와 **완료 응답의 마지막 read가 socket을 닫는 회귀 unit**을 추가했다. 새 실패 경로는 시작 중이던 request도 failed/error로 기록한다. `run02`에서 두 HTML/MP3 취득·전체 decode까지 성공했다. 첫 run에 취득 파일이 없어 이번 실제 재사용/재다운로드는0이다. 같은 경로의 사본 재사용과 URL/bytes/SHA 불일치 거부는 unit으로 검사했으며, 실패 후 검증된 실파일을 재사용한 live retry가 있었다고 보고하지 않는다. 파일 삭제나 자동 삭제 차단 우회는0이다.

### QA matrix의 실제 결과

| ID | 결과 | 증거 |
|---|---|---|
|Q1|canonical source 변경/권리/URL/학습 label 승인 변경 거부|고유 unit·source SHA |
|Q2|실제 CLI4/4 기대 거부: 범위 밖·직접 worker·잘못된 binary·기존 run|[최종 cli-rejections.json](../../work/new-infant-review-20261007/qa03/cli-rejections.json), 각 log; exit1/자손0 |
|Q3|redirect·잘못된 status/URL/type/length·3MiB 초과·HTTP deadline 거부|고유 unit; 실제 HTTP redirect0/timeout0 |
|Q4|검증 사본은 같은 경로에서 재사용; 변조/미기록/중복 record 거부|고유 unit; 최종 QA 뒤 actual MP32개 hash 불변 |
|Q5|known ±.25 PCM RMS/peak/DC·rail 예제·partial bin·≤120초 cap 검증; actual2파일 whole decode|고유 unit·[levels.json](../../work/new-infant-review-20261007/run02/levels.json) |
|Q6|필수 source/rights/format/stat/flags 누락·nonfinite·종료pin 변경 거부; 첫 실패 기록 보존|고유 unit·run01 failure·run02 validated completion |
|Q7|run02 Job180초/512MiB/1MiB, exit0/cleanup complete/remaining0/truncation0|[process.json](../../work/new-infant-review-20261007/run02/process.json), storage start/end |

고유 unit은 **11개**다. 처음10개와 수정 후11개를 별개 기능21개로 합산하지 않는다. 최종 `qa03`의11개 unit은 **.163초 /실패·skip0**이며 외곽 Job의 실제 시간/메모리는 unit-process.json에 기록했다. 최종판에서 CLI4조건을 다시 실행하며 source/code/binary pins를 시작/종료 비교했고 실제 MP3 hash 불변도 확인했다. qa01/qa02의 앞선 검사는 이력으로 보존했다. run98은 기존 run 거부용 빈 owned directory이며 음원이 없다.

`run02` Job은 **16.872759초**, peak aggregate committed **254,259,200B** /limit **536,870,912B**, memory-limit hit0, output truncation0, 자손0이다. 이 값은 Windows Job committed accounting이며 RSS/휴대폰 PSS가 아니다. 시작/종료 storage는 **전용 evidence root 전체**만 측정했다.

| snapshot | file count | logical bytes | drive free bytes |
|---|---:|---:|---:|
|run02 시작|23|16,092|68,262,432,768|
|run02 종료 snapshot 측정 시점|36|866,369|68,261,466,112|

snapshot JSON 자체가 저장되거나 후속 QA가 추가되면 현재 file count/bytes는 달라진다. 전후 값을 임의 재구성하지 않았다. 후속 최종 QA의 실제 용량은 qa03/storage-start.json과 storage-end.json에 별도로 기록했다. 드라이브 free 변화는 공용 드라이브의 변화이며 음원 bytes로만 설명하지 않는다.

### 실행 source/code/binary pins

- canonical source SHA: `91e47d4fabbedea6bf4dc8735b8fd3398398b86da6b2330589f609e7de7ce960`.
- [취득 코드](../../tools/research/public_infant_review_acquire.py) SHA: `26fd8ab846d0c18095006f23ede87897c71b0196d2ea620d05ee7487f2105eb6`.
- [고유 unit 코드](../../tools/research/test_public_infant_review_acquire.py) SHA: `c34f22692690a9b8d996800576e924ced25fe2a3f443d261f532b64aed7879c8`.
- full FFmpeg9.0 SHA: `05f4251bce9293c2ab492cb17ca7724a0ffd0d06c881ba2ee83b82a89c2fc740`; ffprobe SHA: `51e0780cd881f83749b029ed716cbb841c2eac6289f418050f2f2961b158896b`. 기존 WinGet 설치의 `ffmpeg-9.0-full_build/bin/` 경로만 허용했고 취득 전에 s16le muxer를 확인했다.
- runtime: Python3.13.14 /beautifulsoup4 4.15.0. 재사용 helper를 포함한 code8개·binary2개·runtime/source 시작/종료 pins는 run02/launch.json·levels.json 및 qa03/pins-start.json·pins-end.json에서 exact 일치한다. Git을 부르는 `validate_output_path`는 실행하지 않고 `.gitignore`의 `work/` 규칙과 전용 root·symlink/junction 거부를 검사했다.
- [validated-completion.json](../../work/new-infant-review-20261007/run02/validated-completion.json) SHA `cee1c0ca602145a62d9b2f98c97e79340caa117b2f99255d6a907c4fb39b932c`; levels.json SHA `0c8e9718c6c42980034499a05f291c52fbe98350f0d9b16a4b9b7d5b027cf364`; process.json SHA `a17d3a9832855ec570cce368e2eaa93e866c19a37871bdf4df4e164ee2e3f28c`.

### 이번 실제 외부 작업과 남은 경계

official HTML 시도3회 중 재확인 완료2회·첫 EOF 처리 실패1회, exact CDN MP3 GET **2회 /2성공**, 재다운로드0, redirect/timeout0이다. 검색 query0, 원본 AIFF/WAV/feature/weight body 취득0이다. 음원은 지정된 public preview2개만 받았다. 현재 도구 목록에는 원음을 제가 들을 수 있는 청취 API가 없어 **실제 청취0 /사건 판독0**이다. 사용자 또는 별도 인간 검수자가 원음 전체를 듣고 onset/offset·화자·성인/장난감/배경·실제 영아 발성·uncertain를 기록하는 단계가 남았다. 이번 음량 통계나 caption을 그 정답으로 대신하지 않는다.

추론·score·감정 label·학습/calibration·모델 download·private 자료 접근·외부 연락·Git/빌드/ADB·앱 변경은 모두0이다. **완료 범위는 실제 public 음원 취득과 전체 decode·음량 검수 준비이며, 학습 자료 승인·일반 인식 개선·앱 업데이트는 아니다.**
