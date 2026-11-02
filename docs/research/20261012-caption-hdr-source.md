# 갤러리 자막 영상 저장 호환성

사용자 오류는 TranslatedMedia의 Media3 Transformer.onError 공통 메시지다.
PNG 정상만으로 원본 영상의 HDR 여부를 확정하지 않는다. 해당 폰/원본 오류의 전체 cause는 아직 없다.

## 확인한 실제 의존성

- androidx.media3:media3-transformer 및 media3-effect **1.11.1**.
- [공식 HDR 안내](https://developer.android.com/media/media3/transformer/supported-formats)
- [정확 버전 effect 원천](https://dl.google.com/dl/android/maven2/androidx/media3/media3-effect/1.11.1/media3-effect-1.11.1-sources.jar)
- [정확 버전 transformer 원천](https://dl.google.com/dl/android/maven2/androidx/media3/media3-transformer/1.11.1/media3-transformer-1.11.1-sources.jar)

Transformer.start(EditedMediaItem,path)는 기본 Composition(HDR_MODE_KEEP_HDR)을 만든다.
TransformerUtil.getOutputMimeTypeAndHdrModeAfterFallback은 AVC HDR 인코더가 없을 때 HEVC HDR 인코더를
먼저 찾고, 그마저 없을 때 SDR tone mapping으로 간다. 따라서 HDR 인코딩이 가능한 폰과
에뮬레이터의 동작 경로가 다를 수 있다.

OverlayShaderProgram.findHdrTypes는 HDR BitmapOverlay를 Ultra HDR로 취급하며,
drawFrame은 `checkArgument(bitmap.hasGainmap())`를 요구한다. 앱 caption()이 만드는
ARGB 카드에는 gainmap이 없다. HDR을 유지하는 경로에서 자막 오버레이가 실패할 수 있는
실제 코드 결함이다. 에뮬레이터의 일반 SDR 저장 성공으로 이 경로를 검증할 수 없다.

## 수정 및 검사

자막 있는 영상 Composition에 HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL을 지정한다.
자막 없는 선택 구간은 HDR_MODE_KEEP_HDR를 유지한다. 사용자 원본은 수정하지 않는다.
출력되는 자막 영상은 일반 SDR 색상이므로 HDR 원본과 밝기/색감 차이가 있을 수 있다.

1.11.1의 fromSingleItem은 package-private다. 명시 audio+video factory는 없는 소리 트랙을
합성하므로, 공개된 기존 Builder(listOf(movie))로 입력 트랙을 추론한다(해당 함수만 deprecation 억제).
소리 없는 입력에 소리를 만들지 않는 실제 codec 검사로 이 선택을 고정한다.

CaptionHdrCompatibilityTest는 실제 GL shader에서 기존 HDR 경로의 gainmap 실패와
SDR 경로의 draw를 비교하고, 실제 export가 쓰는 Composition 모드를 확인한다.
전체 codec/UI QA 결과와 실폰 미검증 한계는 [QA](../qa/saved-media-20261012.md)에 기록한다.

HDR fixture는 ffmpeg9 libx265로 생성한 3초640×360 testsrc2/PQ BT.2020 +440Hz AAC이며
아기 자료를 포함하지 않는다. 인코딩 옵션은 yuv420p10le, pools=1/frame-threads=1,
smpte2084/bt2020/bt2020nc, ultrafast, faststart다. 오디오·영상 손실/톤매핑 검사를 위한 합성 입력이다.
