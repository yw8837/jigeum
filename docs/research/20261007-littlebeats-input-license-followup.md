# UIUC / LittleBeats 입력·weight 권리 후속

2026-10-07 KST. [representation 감사](20261007-next-infant-representation-evidence.md), [head 라벨 감사](20261007-infant-head-label-evidence.md), [LittleBeats 선행 감사](20261005-infant-specialist-followup.md)를 먼저 대조했다. 새 모델 목록이나 기존 실험을 반복하지 않았다. 소유 파일은 이 문서 하나다.

## 판단

**실행·상업 승인 blocker는 미해결이다.** 이번 새 근거는 LittleBeats의 정규화 선택 수식과 공개 release metadata의 범위를 좁혀 확인한 것이다. exact trained normalizer 파일·적용 weight license·artifact와 recipe의 완전한 연결은 확보하지 못했다. 조사 역량이나 metadata 확인을 모델 품질 검증으로 세지 않는다.

### UIUC

현재 [공식 tree HEAD](https://api.github.com/repos/uiuc-sst/infant-vocalize/git/trees/HEAD?recursive=1)는 기존 `ed1115642231b334c09c345d98b1b3d59d94fe29`와 같고 truncated=false/57 entries다. 새 reference mean/std·선택 index·normalizer artifact·weight grant가 추가되지 않았다. 기존 공개 head와 GPL3/openSMILE 조건은 그대로이며, GPL을 상업 금지로 해석하지 않는다. **head→feature order→훈련 기준 통계 연결 미해결**이다. 같은 util/weight를 다시 실행하지 않았다.

### LittleBeats: 정규화 source에서 새로 좁힌 사실

공식 [infant-voc-classification tree API](https://api.github.com/repos/jialuli3/speechbrain/git/trees/infant-voc-classification?recursive=1)의 ref는 **`7125879536238c72ea9d4f743d9e3a9aca110a71`**, truncated=false다.

- [WA hparams raw](https://raw.githubusercontent.com/jialuli3/speechbrain/7125879536238c72ea9d4f743d9e3a9aca110a71/recipes/wav2vec_LittleBeats/hparams/hparams_LL_4300_WA.yaml)는 `output_norm=true`지만 `input_norm`은 지정하지 않는다. recoverables에도 독립 normalizer가 없다.
- [runner raw](https://raw.githubusercontent.com/jialuli3/speechbrain/7125879536238c72ea9d4f743d9e3a9aca110a71/recipes/wav2vec_LittleBeats/scripts/train_WA_3dnn.py)는 `read_audio`→`batch.sig`→wav2vec2 경로다.
- [wrapper raw](https://raw.githubusercontent.com/jialuli3/speechbrain/7125879536238c72ea9d4f743d9e3a9aca110a71/speechbrain/lobes/models/fairseq_wav2vec.py)141–149에서 `input_norm=None`이면 pretrained checkpoint의 `cfg.task.normalize`, 다음 `cfg.normalize`, 없으면 false를 사용한다.207–208은 활성화 시 `F.layer_norm(wav, wav.shape)`,227–228은 output에도 `out.shape`를 사용한다. **전체 전달 tensor shape**가 정규화 축이므로 batch/padding까지 계약에 포함된다. epsilon은 이 호출에 명시하지 않는다. 새 clip의 dataset mean/std를 fit하는 경로가 아니다.

**공개 checkpoint 내부 normalize flag는 읽지 않았으므로 실제 입력 정규화 활성 여부는 미확정**이다. 독립 scaler 파일이 없다는 사실을 실행 준비 완료로 바꾸지 않는다.

### 공개 weight와 상업 권리

[HF current API](https://huggingface.co/api/models/lijialudew/wav2vec_LittleBeats_LENA?blobs=true)는 기존 revision **`b3250219066482f571ef3868413b0b0f1ad92502`** 그대로다. 알려진 fine-tuned head3,588,884B/encoder377,568,539B는 새 발견이 아니다. 공개 siblings에 normalizer·release hyperparams·LICENSE 파일이 없다.

새로 읽은 [release CKPT.yaml raw](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA/raw/b3250219066482f571ef3868413b0b0f1ad92502/LL_4300_fine_tuned/save/CKPT%2B2022-11-26%2B14-06-17%2B00/CKPT.yaml)는 end-of-epoch/error/loss/time뿐이며 normalize flag·recipe·license 연결을 제공하지 않는다. YAML 객체를 역직렬화하지 않고 텍스트만 읽었다.

[현재 card raw](https://huggingface.co/lijialudew/wav2vec_LittleBeats_LENA/raw/b3250219066482f571ef3868413b0b0f1ad92502/README.md)의 권리 표시는 여전히 generic `license: openrail`이다. 특정 전문/버전·weight 상업 이용/재배포 grant는 확보하지 못했다. card가 연결한 `jialuli3/wav2vec_LittleBeats_LENA`와 이번 branch를 같은 release 구현으로 자동 합치지 않는다. SpeechBrain Apache2가 weight·가족 녹음 허락을 증명하지 않는다. **상업 이용·distillation 승인 보류**다.

## 실제 조회·pins

외부 **8/8 GET**, 모두200/각20초 timeout/실제 .752초 이하/실패·timeout0. 검색0. API3+raw5이며 API bytes는 metadata, raw는 source/YAML/card 텍스트다. Git tree ref를 별도 commit SHA라고 주장하지 않는다.

| 순서·본문 | bytes | SHA256 |
|---|---:|---|
|1 UIUC tree|13,369|5c9407a2bc52e59c78efcc68cb6195f3d6b76451a0ebf73adf7f4c40d8354ca4|
|2 recipe tree|300,746|083b3a99f79052a7a259793d37882310b5b715733c18a1921f5153b3871cc796|
|3 HF API|4,568|c5e10b3ebee54e4b17ccae4e34c3240ea63ca863be27d975f979df2c034556b5|
|4 WA yaml|4,085|1ea7462cbaf52e028e5bc51b1de2e648e701bd1a89870b81c2a346862b3dee3a|
|5 runner|12,199|0f3673291fd7cad503e1b4e4fd8fbb0e479b94cbf68a2eca72308a0258faed8c|
|6 wrapper|14,541|522e1820708ff82df139e24e7d7e440fc94bd8a008a4f49513ce4dd36876cc49|
|7 CKPT.yaml|341|1c32483db269080caaedf3c4a60f3b9420b646a87681b59284fda1e3692276d8|
|8 card|8,946|c786af08643067f98c732a54855c1a5669b127bb79e60c56d4acdda2ad5d4099|

조회 시각06:44:54–06:45:48 KST. weight/원음 취득·훈련·추론·Git·ADB·삭제·사용자 자료 접근·연락은 모두0이다. 다음에 필요한 근거는 exact checkpoint 설정→release recipe 연결 및 구체적인 weight 사용 조건이며, 이번에는 받거나 문의하지 않았다.
