"""Capture baseline preferences before an in-place debug upgrade. Does not modify app data."""
import json
import xml.etree.ElementTree as ET
import qa_harness as q

if __name__ == "__main__":
    raw = q.adb("exec-out", "run-as", q.PACKAGE, "cat", "shared_prefs/moa_observations.xml").stdout
    root = ET.fromstring(raw)
    node = root.find("string[@name='events']")
    if node is None or not json.loads(node.text or "[]"):
        raise SystemExit("Save at least one synthetic record with the baseline app before capture.")
    (q.RUN / "legacy-before.xml").write_bytes(raw)
    print("Captured synthetic baseline records; install the current debug APK with adb install -r next.")
