# UltraQA Report — 별도 공개 음성의 동결 host/Android 평가

2026-10-07. [사전 계약](../research/20261007-public-infant-pair-evaluation-contract.md),
[실제 결과](../research/20261007-public-infant-followup-results.md). 전체goal active/제품code32 유지다.

## Goal and success criteria

exact권리/source3·고정모델/head·actual decode/전체사전구간·완전결과·실패/거짓성공방어·기존판정보존을검사한다.
host추론과실제Android판정을구분하고head지원만으로앱회복·사건정답·일반정확도를보고하지않는다.
사용자자료읽기/전송/학습0, calibration/threshold변경0, 새build/모델download0,
production/상품/실거래0이다.최대5cycle/동일실패3회정지·각작업timeout을유지한다.
이한정QA완료는부모goal/ARM/배포완료와구분한다.범위밖dirty변경을되돌리지않는다.

## Scenario matrix

| ID | 의도·사용자/공격 | setup/명령 | 기대 | 실제 | 수정·증거 | 정리 |
|---|---|---|---|---|---|---|
|P1|정상exactsource/첫중간끝사전선택|public_infant_followup.py run01/02|3완전case/14창·종료pin|cycle1 codec실패/cycle2 3case14창완료|fullFFmpeg/muxer사전검사·run02evaluation/process|PCMfile0·public3한세트중복삭제차단 |
|P2|외부/기존output·missing model/head/ffmpeg|qa_cli.py,각30초|취득전거부/미생성|초기5/후속6전체거부|qa/cli*.json,로그|fixture/file미생성 |
|P3|SSRF/권리/승인/redirect/bytecap변조|fixedsource/unit·공통HTTPguard|network전에고정값거부|14고유unit내경계검사통과|test_public_infant_followup/test_extra_babble_evaluation|inmemory only |
|P4|NaN/shape/parity·partial/fakecomplete/codepins누락|unit/resultverifier|실패로거부·false품질flag|unit통과·실제완료검증|종료model/head/fit/source/labels/FFmpeg/codepin|텐서zeroing |
|P5|long/timeout/worker/state/dirty|boundedJob180초3GiB1MiB/OMX격리|자손0/clear부재·범위밖보존|run01/02각자손0·절단0|processjson/qa scoped state|stateclear·실제부재확인 |
|P6|자료drift·소급성공/중복원음|pins/URL/SHAreuse/삭제경계|실패보존·원본보호·public한세트|pins보존·중복MP3삭제정책차단|sha-before/after·실패로그·삭제시도설명|중복3파일남김·우회0 |
|A1|hostscore와전체판정차이|동결pair/public5+기존control3|8완전case·동일PCM·보류보존|계측1pass/8측정·새한마디0|android01manifest/pairedresults/instrumentation|UUIDcopy없음 |
|A2|기존승인/비아기오인|동일기존phase/보완phase|승인/대조exact보존|기존승인3·대조3exact|qa_android.py assertions/result|원MP3/controlPCM불변 |
|A3|wrongdevice/pair/partial/fakeOK|MoaQA35+installedhash/strictparser|명시기기/pair·skip0|APK/testexact·1pass/0fail/0skip|installed.json·execution|연구앱2없음 |
|A4|shared기기/디스크/프로세스|start/endstorage/production snapshot|원앱불변·AVD실행종료|production전후미설치exact·기기/실행0|android01result·ADB/프로세스확인|공용AVDfolder보존 |

command/LLM입력을제품에추가하지않았다.입력주입은fixedURL/filename/권리/stale/거짓성공경계로다룬다.
품질정답을확보하지못해caption을실제옹알이/웃음/울음label로채우지않는다.

## Commands run

cwd는활성worktree,Python은`work/ast-portability/venv/Scripts/python.exe`다.
실제증거root는ignored `work/public-infant-followup-20261007/`다.

| command/evidence | exit | 측정/timeout |
|---|---:|---|
|사전unit초기잘못된repo cwd|1(unitrunner)|moduleimport2실패·추론/제품결함아님 |
|tools/research cwd unit baseline|0|13pass/.383초·처음출력;qa/unit.log별도13pass |
|qa_control.py start/retry|0|격리CLIstate의exit/success검사·후속14고유unit/.376초,unit-cycle2.log |
|public_infant_followup.py run01|1|31.612891초/Job180초·codec실패·취득3완료/추론결과0 |
|qa_decode.py|0진단|각fileexit4294967274/output0·공통muxer미지원;입력별3개codec진단 |
|public_infant_followup.py run02 --reuse run01|0|13.661301초/Job180초·3case14창·자손0·log절단0 |
|qa_cli.py 초기/후속|0|5→6고유rejection조건·각30초;새muxer실제거부추가 |
|qa_android.py|0|계측1pass/8측정/37.65초·instrumenttimeout300초 |
|duplicate MP3 cleanup nativePowerShell|실행전거부|자동검토`blocked by policy`·삭제0·다른도구우회0 |
|qa_control.py blocked|초기2회1/후속0|read/clear응답schema하네스수정·최종cleanup/activefalse blocked→read→clear→read부재 |

고유unit은14개다.수정전13과수정후14,동일테스트재실행을합산하지않는다.
CLI고유조건6,Androidtest ID1안의실제측정8이다.예전Android144회와합쳐새검사개수로보고하지않는다.

## Failures found and fixes applied

1. 초기unit호출cwd가repo라import실패:tools/research로수정.실제13/14pass를남겼다.
2. Remotion전용FFmpeg는raws16le muxer가없다.원음불량/모델오류가아닌harness설정실패다.
   설치된fullFFmpeg9.0으로변경·외부취득전muxer확인/unit1/실제CLI1거부추가,
   run02는hash검증reuse로다운로드0이다.원래실패와진단은소급녹색으로바꾸지않는다.
3. hosthead가high인9개월tail도전체phase는보류됐다.실제YAMNet울음충돌guard가원인이다.
   충돌을지워neutral로바꾸거나threshold를낮추지않았다.추가정보이득이며해결완료라고보고하지않는다.
4. duplicatecleanup은명시적으로스코프/SHA/유지사본을검사하는명령이었지만자동승인검토가실행전체를차단했다.
   표시이유는`blocked by policy`이며더세부사유는제공되지않았다.삭제/부수적finish도실행되지않았다.
   state종료는별도비파괴명령으로수행했다.중복3파일2,190,312B는그대로보존한다.
5. QA상태하네스가write의`success`를read/clear에도기대했다.실제read는rawstate,
   clear는`cleared:true`다.첫read검증실패,후속clear는실제로성공했으나검증실패를기록했다.
   각action의실제schema로수정해최종read/clear와파일부재를확인했다.제품오류/원음실험실패가아니다.

## Cleanup and rollback / evidence

이번소유root의시작snapshot은2026-10-06T20:30:30.431530Z,
1file/3,468logicalB/Cfree68,324,675,584B로실제관측했다.과거시작값으로대신하지않았다.
성공run02는start0B/end9files2,213,641B/Cfree68,325,847,040B였다.
Androidrun은start0B/end5files726,938B/Cfree68,295,266,304B였다.
각snapshot은그snapshot쓰기전수치이며폴더logicalbytes/전체드라이브여유다.가상디스크실제회수량이아니다.
마지막QAroot snapshot은qa/storage-end.json에남겼다.실패run01/rawMP3중복은삭제차단으로남아있다.
현재rawMP3총6파일4,380,624B다.새PCMhostfile/APK/AAB/weight0,공용AVD실행과연구앱은정리했다.
기존app/build/hostclasses/controlledPCM삭제차단도우회하지않았다.기존단계BLOCKED를소급완료하지않는다.

## Residual risks

실제사건/화자/月령·자연성groundtruth0,원영상AV0,실폰ARM0,일반정확도/욕구번역승인0이다.
세publicsource반응이전약국한원본회복을일반화하지않으며성인/TV/감정혼합을더확인해야한다.
호스트출력과Android판정의차이를숨기지않는다.제품0.5.18/code32이후새패키지/Play제출0이다.

**ULTRAQA BLOCKED: duplicate public-preview cleanup was rejected by automatic policy review.**
행동검사는cycle2에통과했고,추가Android판정은보존검사를통과했다.정리경계가남아전체QA완료로표시하지않는다.
다음실행은실제ARM proof와감정충돌의안전한표현계약검토다.전체goal은active다.
