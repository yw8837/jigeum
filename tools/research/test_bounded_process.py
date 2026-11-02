"""Synthetic Windows process-tree regressions. No models, media, or Android."""
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import time
import unittest
import psutil

MIB = 1024**2
SLEEPER = "import os,time; print('PID',os.getpid(),flush=True); time.sleep(8)"


def child_program(depth):
    if depth == 0:
        return SLEEPER
    return ("import os,subprocess,sys,time; print('PID',os.getpid(),flush=True); "
            f"subprocess.Popen([sys.executable,'-c',{child_program(depth - 1)!r}]); time.sleep(8)")


def assert_pid_absent(test, pid):
    # Job accounting reaches zero before Windows finishes removing PID entries.
    deadline = time.monotonic() + .5
    while psutil.pid_exists(pid) and time.monotonic() < deadline:
        time.sleep(.005)
    test.assertFalse(psutil.pid_exists(pid), f"PID still present after cleanup: {pid}")


class BoundedProcessTest(unittest.TestCase):
    def setUp(self):
        evidence = os.environ.get("BOUNDED_QA_DIR")
        self.temp = None if evidence else tempfile.TemporaryDirectory()
        self.directory = Path(evidence or self.temp.name) / self._testMethodName
        self.directory.mkdir(parents=True, exist_ok=False)

    def tearDown(self):
        if self.temp:
            self.temp.cleanup()

    def run_program(self, code, timeout=3, memory=256*MIB, output=1024*MIB):
        from bounded_process import run_bounded
        from dataclasses import asdict
        log = self.directory / "output.log"
        result = run_bounded([sys.executable, "-c", code], self.directory,
                             log, timeout, memory, max_output_bytes=output)
        self.directory.joinpath("result.json").write_text(json.dumps(asdict(result), indent=2), encoding="utf-8")
        self.assertTrue(result.cleanup_complete)
        self.assertEqual(result.remaining_job_processes, 0)
        assert_pid_absent(self, result.root_pid)
        text = log.read_text(encoding="utf-8")
        for line in text.splitlines():
            if line.startswith("PID "):
                assert_pid_absent(self, int(line[4:]))
        return result, text

    def test_supervisor_exists_and_retains_normal_output(self):
        spec = importlib.util.find_spec("bounded_process")
        self.assertIsNotNone(spec, "Missing process-tree supervisor; launcher-only checks cannot enforce child limits")
        result, text = self.run_program("print('normal-output')")
        self.assertEqual(result.exit_code, 0)
        self.assertEqual(text.strip(), "normal-output")
        self.assertIsNone(result.termination)
        self.assertGreater(result.peak_job_committed_bytes, 0)

    def test_misleading_success_does_not_hide_nonzero_exit(self):
        result, text = self.run_program("import sys; print('SUCCESS'); print('failure',file=sys.stderr); sys.exit(7)")
        self.assertEqual(result.exit_code, 7)
        self.assertIn("SUCCESS", text)
        self.assertIn("failure", text)
        self.assertIsNone(result.termination)

    def test_timeout_kills_launcher_child_and_grandchild(self):
        result, text = self.run_program(child_program(2), timeout=1)
        self.assertEqual(result.termination, "timeout")
        self.assertGreaterEqual(text.count("PID "), 3)
        self.assertLess(result.elapsed_seconds, 3)

    def test_normal_root_exit_cleans_orphan_with_inherited_stdout(self):
        code = ("import subprocess,sys,time; "
                f"subprocess.Popen([sys.executable,'-c',{SLEEPER!r}]); time.sleep(.3)")
        result, text = self.run_program(code)
        self.assertEqual(result.exit_code, 0)
        self.assertEqual(result.termination, "orphan_cleanup")
        self.assertIn("PID ", text)

    def test_hard_memory_limit_denies_allocation_in_real_launcher_child(self):
        # A huge allocation is refused by the kernel job; no polling RSS race.
        code = "import os; print('PID',os.getpid(),flush=True); x=bytearray(256*1024**2); print('ALLOCATED',flush=True)"
        result, text = self.run_program(code, memory=96*MIB)
        self.assertNotEqual(result.exit_code, 0)
        self.assertNotIn("ALLOCATED", text)
        self.assertTrue(result.memory_limit_hit)
        self.assertEqual(result.termination, "memory_limit")
        # Windows job accounting can include the refused allocation attempt.
        # A configured cap or peak comparison alone is not an execution gate.
        self.assertGreater(result.peak_job_committed_bytes, 0)

    def test_aggregate_limit_covers_multiple_children(self):
        worker = ("import os,time; from pathlib import Path; print('PID',os.getpid(),flush=True); "
                  "x=bytearray(48*1024**2); print('ALLOCATED',flush=True); Path('ready').touch(); time.sleep(8)")
        code = ("import subprocess,sys,time\nfrom pathlib import Path\n"
                f"subprocess.Popen([sys.executable,'-c',{worker!r}])\n"
                "deadline=time.monotonic()+1\n"
                "while not Path('ready').exists() and time.monotonic()<deadline: time.sleep(.005)\n"
                f"subprocess.Popen([sys.executable,'-c',{worker!r}])\ntime.sleep(8)")
        result, text = self.run_program(code, memory=110*MIB)
        self.assertTrue(result.memory_limit_hit)
        self.assertEqual(result.termination, "memory_limit")
        self.assertGreater(result.peak_job_committed_bytes, 0)
        self.assertGreaterEqual(text.count("PID "), 2)
        self.assertEqual(text.count("ALLOCATED"), 1)
        self.assertLess(text.count("ALLOCATED"), 2)

    @unittest.skipUnless(sys.executable != sys._base_executable, "requires Windows venv launcher")
    def test_launcher_rss_is_not_the_actual_child_or_job_memory(self):
        code = ("import json,os,psutil; x=bytearray(48*1024**2); p=psutil.Process(); "
                "print(json.dumps(dict(pid=os.getpid(),parent_pid=p.ppid(),child_rss=p.memory_info().rss,"
                "launcher_rss=p.parent().memory_info().rss)),flush=True)")
        result, text = self.run_program(code)
        data = json.loads(text)
        self.assertEqual(result.root_pid, data["parent_pid"])
        self.assertNotEqual(result.root_pid, data["pid"])
        self.assertGreater(data["child_rss"], data["launcher_rss"] + 20*MIB)
        self.assertGreater(result.peak_job_committed_bytes, data["launcher_rss"] + 20*MIB)
        assert_pid_absent(self, data["pid"])

    def test_output_flood_has_exact_disk_bound_and_cleanup(self):
        result, text = self.run_program("import sys; sys.stdout.write('x'*1000000); sys.stdout.flush()", output=4096)
        self.assertEqual(result.termination, "output_limit")
        self.assertEqual(result.output_bytes, 4096)
        self.assertEqual(len(text), 4096)

    def test_log_collision_and_invalid_args_rejected_before_work(self):
        from bounded_process import run_bounded
        log = self.directory / "occupied.log"
        log.write_text("preserve", encoding="utf-8")
        marker = self.directory / "started"
        args = [sys.executable, "-c", f"from pathlib import Path; Path({str(marker)!r}).touch()"]
        with self.assertRaises(FileExistsError):
            run_bounded(args, self.directory, log, 2, 256*MIB)
        self.assertEqual(log.read_text(), "preserve")
        for i, changes in enumerate((dict(argv=[]), dict(argv="echo bad"), dict(argv=[sys.executable,"\0"]),
                                     dict(timeout_seconds=0), dict(timeout_seconds=float('nan')),
                                     dict(memory_limit_bytes=-1), dict(memory_limit_bytes=1.5),
                                     dict(max_output_bytes=0), dict(cwd=self.directory/'absent'),
                                     dict(env={"BAD=KEY":"value"}))):
            with self.subTest(changes=changes):
                params = dict(argv=args, cwd=self.directory, log_path=self.directory/f"invalid-{i}.log",
                              timeout_seconds=2, memory_limit_bytes=256*MIB)
                params.update(changes)
                with self.assertRaises((ValueError, TypeError, FileNotFoundError)):
                    run_bounded(**params)
                self.assertFalse(Path(params["log_path"]).exists())
        self.assertFalse(marker.exists())

    def test_argv_quoting_and_explicit_environment(self):
        from bounded_process import run_bounded
        script = self.directory / "한글 space script.py"
        script.write_text("import os,sys; print(sys.argv[1]); print(os.environ['BOUNDED_VALUE'])", encoding="utf-8")
        env = os.environ.copy()
        env["BOUNDED_VALUE"] = "한글 value"
        env["PYTHONIOENCODING"] = "utf-8"
        log = self.directory / "quoted.log"
        result = run_bounded([sys.executable, str(script), 'space ; & "quote" 끝'], self.directory,
                             log, 3, 256*MIB, env=env)
        self.assertEqual(result.exit_code, 0)
        self.assertTrue(result.cleanup_complete)
        assert_pid_absent(self, result.root_pid)
        self.assertEqual(log.read_text(encoding="utf-8").splitlines(), ['space ; & "quote" 끝', '한글 value'])


if __name__ == "__main__":
    unittest.main(verbosity=2)
