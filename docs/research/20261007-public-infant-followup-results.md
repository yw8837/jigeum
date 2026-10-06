# 별도 공개 영아 음성: 동결 발성 head의 추가 반응

2026-10-07. 기준 `6511377`, [사전 계약](20261007-public-infant-pair-evaluation-contract.md).
약국 누락 회복 후 동결 후보를 별도 공개자료3개에 처음 적용했다. **세파일 모두 지원창이 있지만 사건 정답/일반 정확도는 미입증**이다.
사용자 원음·특징 읽기/전송·학습·calibration·threshold 변경0, 새모델/encoder/LLM 다운로드0이다.

## 고정한 비교와 실제 실행

- source SHA `ba345dd1dab51e0f0cc0d90805e5ba54b5aab902962ed07bae3ceb6f6ffbff2d`.
- graph91,138,597B/SHA `4a378476dcbe5f6b13c5f67295797d59376dab3627fe3dbd56bfeeadbd7f802a`.
- head SHA `81d7357c4c59f57222952a1542716f7d89e423d45f2987dd3917a3522bdfd510`, 769parameter/threshold.8/margin.4 불변.
- Python3.13.14/NumPy2.4.6/ONNX1.20.1/ORT1.24.4, CPU4threads. PC 측정이며 휴대폰 지연/메모리가 아니다.
- exact CDN MP33파일 총 **2,190,312B**, CC0/caption-only/trainingblocked/approvednull.
  재시도는 처음 취득의 URL/byte/SHA 검증 후 재사용했으며 재다운로드0이다.
- 처음 두 파일은 첫≤20초, 475589는 실제272.161125초의 첫·중간·끝 각20초를 점수 계산 전에 정했다.
  이를10초/5초hop/끝창의 **14 context windows**로 분석했다. 실제 사건 시작/길이를 뜻하지 않는다.
- 모든14창은 rawRMS gate를 통과했다. original527 logits는 shape/dtype/finite를 확인하고 class별sigmoid를 적용했다.
  graph/head NumPy parity 기준5e-5를 확인했다. labels/model/head/fit/source/FFmpeg와 실행code 종료pin을 확인했다.

첫실행 `work/public-infant-followup-20261007/run01`은 Remotion 전용FFmpeg가 `s16le` outputmuxer를
제공하지 않아 **추론 결과 없이 exit1**로 종료됐다.32초 Job/자손0/취득3파일은 보존했다.
기존 설치된 fullFFmpeg9.0으로 변경하고, 외부취득 전에 muxer를 확인하는 방어를 추가했다.
성공 `run02`는 **13.661301초/exit0/자손0**, peakJob committed1,656,041,472B/limit3GiB,
time cap180초/log cap1MiB/절단0이다. 이peak는host전체Job이며 AndroidPSS로 쓰지 않는다.

## 모든 사전 선택 구간의 결과

| exactID·공식 caption | 선택 구간 초 | 지원창/전체창 | head score 전체 | originalAST 관련 score 범위 |
|---|---:|---:|---|---|
|388407·15개월 아들giggling|0–10.913375|2/2|.9595,.9681|Babbling.2835–.3547;Babylaughter.3069–.3490 |
|135185·한살 쌍둥이목욕/웃음tag|0–20|3/3|.9388,.9282,.9497|Babbling.0111–.0388;Babylaughter.0344–.1067 |
|475589·9개월여아babble/trill/끝littlecry|0–20|0/3|.0646,.1013,.0312|Babbling.00016–.00043;Babycry.000018–.000084 |
|동일475589|126.0805625–146.0805625|2/3|.7770,.9227,.8404|Babbling.0122–.0955;Babycry.2830–.3145 |
|동일475589|252.161125–272.161125|2/3|.2680,.9544,.9353|Babbling.0012–.0220;Babycry.00019–.0048 |

첫20초미지원과중간첫창.7770미지원을 그대로 남겼다. caption은 전구간 발성을 보장하지 않으므로
0/3을 옹알이 false-negative3건으로, 마지막2/3을 울음 정답2건으로 채우지 않는다.
세파일/9지원창은 caption-weak 평가의 기술적 반응이며 **9/14 정확도·세가족 독립 검증**이 아니다.
475589의 uploader는 기존색인에 있으므로 새아동/가족으로 세지 않는다. [원문 조사](20261007-new-natural-babble-sources.md).

host단계는Android cleaned strongest-second RMS/YAMNet/phase/문장을 실행하지 않았다.
head support를 앱전체해석/옹알이·울음·욕구 정답/배포 효과로 보고하지 않는다.

## 동일APK의실제Android전체음성판정

host후사전추가계약A1–A4에따라기존동결researchAPK/test를MoaQA35/API35/x86_64에설치했다.
앱 SHA `77b20be46ecef6e302515d63bde12f4c378a8eeb50ea829fb455d0271a61624e`,
test SHA `3dea96314dbb0fe8daafecbdfed051e8a9d7b72ed774a2c02c590027bb184159`.
actualinstalledbaseAPK hash/code32를확인했고새build/weightdownload0이다.
기존YAMNet+shifted/ASTphase와presence보완phase를동일PCM으로비교했다.

| 고정입력 | 기존phase | presence보완phase | 관찰 |
|---|---|---|---|
|388407 첫10.913375초|아기웃음/한마디|exact동일|지원된결과보존 |
|135185 첫20초|아기웃음/한마디|exact동일|지원된결과보존 |
|475589 첫20초|INFANT_UNCONFIRMED|동일보류|head도미지원 |
|475589 중간20초|울음/한마디|exact동일|head점수로울음을neutral로바꾸지않음 |
|475589 마지막20초|INFANT_UNCONFIRMED|동일보류|head지원2창이지만YAMNet울음충돌로generic보완차단 |
|기존conversation|INFANT_UNCONFIRMED|exact동일|새지원0 |
|기존piano|BACKGROUND_DOMINANT|exact동일|새지원0 |
|기존cchenail_laugh|INFANT_UNCONFIRMED|exact동일|새지원0 |

**새public5선택에서는새한마디회복0**, 기존한마디3/3·대조3/3의fullobservation/문장을exact보존했다.
host의9지원창을앱새지원9개로바꾸지않는다.8측정/계측1pass/실패·skip0,
37.650초ADB실행(기기JUnit35.726초). 이는에뮬레이터의음성경로이며원영상AV/실폰성능시험이아니다.

마지막20초에서head.9544/.9353과같은창cleanedRMS.03991은기준을넘었다.
그러나YAMNet에는울음.5/화자미확인울음.66797이있어`BabbleSupportPolicy.shouldAnalyze`의
기존울음≥.35/일반울음≥.65보호에서탈락했다. head도넓은10초문맥이고발성의정확위치/화자를입증하지않아
다른화자의울음이섞인장면을아기감정으로바꾸는위험이있다. **출력보류원인이음량만은아님**을실제전체경로에서확인했다.
이한caption파일의tail에맞춰충돌guard/threshold를완화하지않았다.
후속은발성존재단서와감정단서를분리한표현계약/같은시간창·성인감정혼합반례검토이며,
이자료를울음정답이나새학습자료로전환하지않는다.

UUIDruntimePCM/연구앱2개는제거,원MP3/controlPCM불변·기존production설치상태불변을확인했다.
이번에띄운공용AVD실행은종료·ADB기기0,AVDfolder는보존했다.
이번시각관찰/원영상AV·실폰은0이다.

## 정답 자료 조사와 다음

[ACLEW/BabbleCor 추가원문](20261007-babble-event-label-next.md)은 who/when·canonical/noncanonical과
adult/electronicmedia annotation 경로를 확인했다. exactEAF/음원·상업허락은 아직 미확보다.
caption만으로 정답을 만들거나 기존모델 출력을 새정답으로 쓰지 않는다.

필요한 다음검사는감정충돌의보류원인/안전한표현계약과S25 ARM속도/발열/취소검증이다.
유효변경만새code≥33 beta로반영하며현재Play0.5.18/code32에는새presence를배포하지않았다.
전체goal은active다. 실험자료는작은publicMP3한세트만후속사건검수용으로보존한다.
