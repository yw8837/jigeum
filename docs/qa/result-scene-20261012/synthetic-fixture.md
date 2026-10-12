# 합성 날짜 회귀 자료

기존 합성 `media-quadrants.mp4`를 재인코딩 없이 복사하고 MP4 creation_time만 2020-01-01T00:00:00Z로 고정했다. 실제 아기/사용자 영상이 아니다. androidTest 자산이며 제품 APK/AAB에 포함하지 않는다.

```text
ffmpeg -i media-quadrants.mp4 -c copy -metadata creation_time=2020-01-01T00:00:00Z media-quadrants-old-date.mp4
```

SHA256 `de1641ebcaf8f7d1b37106fb0024376ab96a6cd7356b61d0af23617b599b3765`
