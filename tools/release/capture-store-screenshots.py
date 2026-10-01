"""Capture real, billing-disabled release screens on the dedicated QA emulator.

Synthetic example phrases only. Never run on a device containing family records.
Uses the existing bounded UI harness, without entitlement overrides or DB writes.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "outputs/QA"))
import qa_harness as q


def capture():
    q.launch()
    q.screenshot("store-01-home")
    q.tap_text("소리로 번역하기")
    q.tap_text("녹음 없이 직접 번역")
    q.tap_text("옹알이")
    q.tap_text("놀이 중")
    q.tap_text("아기말 문장 보기")
    q.wait_ui("가능한 한마디")
    q.screenshot("store-02-expression")
    q.tap_text("이 문장 저장하기")
    q.wait_ui("우리가 나눈 아기말")
    q.screenshot("store-03-record")
    q.launch()
    q.tap_text("아기말북", scroll=False)
    q.wait_ui("한 달의 말")
    q.screenshot("store-04-album")
    q.launch()
    q.tap_text("카메라로 살펴보기", exact=False)
    q.wait_ui("구독 확인 서비스가 아직 설정되지 않았어요")
    q.screenshot("internal-billing-disabled")
    q.launch()
    result = {"status": "pass", "screens": 4, "source": "upload-key-signed release APK",
              "data": "one synthetic phrase saved through actual UI",
              "billing": "disabled; real Play/paid features not tested", "physical_ARM": False}
    (q.RUN / "store-capture-result.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))


def main():
    original = q.display_settings()
    try:
        # Play screenshots require the long edge <= 2x the short edge. Capture
        # the actual responsive UI at 1080x2160; do not crop a taller screenshot.
        q.shell("wm", "size", "1080x2160")
        capture()
    finally:
        q.restore_display(original)


if __name__ == "__main__":
    main()
