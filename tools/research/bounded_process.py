"""Windows-only bounded argv execution, including venv launcher descendants.

The Job Object hard limit is aggregate committed virtual memory, NOT RSS or
working set. PeakJobMemoryUsed is raw kernel job accounting, not a sampled PID
peak; Windows can count a refused allocation attempt in that peak. A peak above
the configured limit is therefore not proof that the allocation was admitted.
Children inherit the job (breakaway is not enabled). This is a resource boundary
for trusted local research commands, not a sandbox for hostile native programs.
Importing this module starts no process and creates no handles.
"""
from dataclasses import dataclass, replace
import ctypes as C
from ctypes import wintypes as W
import math
import os
from pathlib import Path
import shutil
import subprocess
import threading
import time


@dataclass(frozen=True)
class BoundedResult:
    root_pid: int
    exit_code: int
    elapsed_seconds: float
    termination: str | None
    cleanup_complete: bool
    remaining_job_processes: int
    peak_job_committed_bytes: int
    memory_limit_bytes: int
    memory_limit_hit: bool
    output_bytes: int
    output_truncated: bool


def _validate(argv, cwd, log_path, timeout, memory, env, output):
    if isinstance(argv, (str, bytes)) or not isinstance(argv, (list, tuple)) or not argv:
        raise ValueError("argv must be a nonempty list/tuple; shell commands are not accepted")
    if any(not isinstance(x, str) or '\0' in x for x in argv) or not argv[0]:
        raise ValueError("argv entries must be strings without NUL")
    if isinstance(timeout, bool) or not isinstance(timeout, (int, float)) or not math.isfinite(timeout) or timeout <= 0:
        raise ValueError("timeout_seconds must be finite and positive")
    for name, value in (("memory_limit_bytes", memory), ("max_output_bytes", output)):
        if isinstance(value, bool) or not isinstance(value, int) or not 0 < value <= 2**63-1:
            raise ValueError(f"{name} must be a positive integer <= 2**63-1")
    cwd = Path(cwd).resolve(strict=True)
    if not cwd.is_dir():
        raise ValueError("cwd must be a directory")
    log_path = Path(log_path).absolute()
    if not log_path.parent.is_dir():
        raise FileNotFoundError(log_path.parent)
    if env is not None:
        if not isinstance(env, dict):
            raise ValueError("env must be a string mapping or None")
        if any(not isinstance(k, str) or not k or '=' in k or '\0' in k or
               not isinstance(v, str) or '\0' in v for k, v in env.items()):
            raise ValueError("env keys/values must be valid Windows environment strings")
    child_env = dict(os.environ if env is None else env)
    executable = shutil.which(argv[0], path=child_env.get("PATH", ""))
    if executable is None:
        raise FileNotFoundError(argv[0])
    executable = str(Path(executable).resolve(strict=True))
    if not Path(executable).is_file():
        raise ValueError("executable must be a file")
    return [executable, *argv[1:]], cwd, log_path, child_env


class _BasicLimit(C.Structure):
    _fields_ = [("PerProcessUserTimeLimit", C.c_longlong), ("PerJobUserTimeLimit", C.c_longlong),
                ("LimitFlags", W.DWORD), ("MinimumWorkingSetSize", C.c_size_t),
                ("MaximumWorkingSetSize", C.c_size_t), ("ActiveProcessLimit", W.DWORD),
                ("Affinity", C.c_size_t), ("PriorityClass", W.DWORD), ("SchedulingClass", W.DWORD)]


class _IOCounters(C.Structure):
    _fields_ = [(n, C.c_ulonglong) for n in ("ReadOperationCount", "WriteOperationCount", "OtherOperationCount",
                                            "ReadTransferCount", "WriteTransferCount", "OtherTransferCount")]


class _ExtendedLimit(C.Structure):
    _fields_ = [("BasicLimitInformation", _BasicLimit), ("IoInfo", _IOCounters),
                ("ProcessMemoryLimit", C.c_size_t), ("JobMemoryLimit", C.c_size_t),
                ("PeakProcessMemoryUsed", C.c_size_t), ("PeakJobMemoryUsed", C.c_size_t)]


class _Accounting(C.Structure):
    _fields_ = [(n, C.c_longlong) for n in ("TotalUserTime", "TotalKernelTime", "ThisPeriodTotalUserTime", "ThisPeriodTotalKernelTime")]
    _fields_ += [(n, W.DWORD) for n in ("TotalPageFaultCount", "TotalProcesses", "ActiveProcesses", "TotalTerminatedProcesses")]


class _Port(C.Structure):
    _fields_ = [("CompletionKey", C.c_void_p), ("CompletionPort", W.HANDLE)]


class _Security(C.Structure):
    _fields_ = [("nLength", W.DWORD), ("lpSecurityDescriptor", C.c_void_p), ("bInheritHandle", W.BOOL)]


class _Startup(C.Structure):
    _fields_ = [("cb", W.DWORD), ("lpReserved", W.LPWSTR), ("lpDesktop", W.LPWSTR), ("lpTitle", W.LPWSTR),
                ("dwX", W.DWORD), ("dwY", W.DWORD), ("dwXSize", W.DWORD), ("dwYSize", W.DWORD),
                ("dwXCountChars", W.DWORD), ("dwYCountChars", W.DWORD), ("dwFillAttribute", W.DWORD),
                ("dwFlags", W.DWORD), ("wShowWindow", W.WORD), ("cbReserved2", W.WORD),
                ("lpReserved2", C.c_void_p), ("hStdInput", W.HANDLE), ("hStdOutput", W.HANDLE), ("hStdError", W.HANDLE)]


class _StartupEx(C.Structure):
    _fields_ = [("StartupInfo", _Startup), ("lpAttributeList", C.c_void_p)]


class _Process(C.Structure):
    _fields_ = [("hProcess", W.HANDLE), ("hThread", W.HANDLE), ("dwProcessId", W.DWORD), ("dwThreadId", W.DWORD)]


def _kernel():
    k = C.WinDLL("kernel32", use_last_error=True)
    signatures = {
        "CreateJobObjectW": ([C.c_void_p, W.LPCWSTR], W.HANDLE),
        "SetInformationJobObject": ([W.HANDLE, C.c_int, C.c_void_p, W.DWORD], W.BOOL),
        "QueryInformationJobObject": ([W.HANDLE, C.c_int, C.c_void_p, W.DWORD, C.c_void_p], W.BOOL),
        "CreateIoCompletionPort": ([W.HANDLE, W.HANDLE, C.c_size_t, W.DWORD], W.HANDLE),
        "GetQueuedCompletionStatus": ([W.HANDLE, C.POINTER(W.DWORD), C.POINTER(C.c_size_t), C.POINTER(C.c_void_p), W.DWORD], W.BOOL),
        "CreatePipe": ([C.POINTER(W.HANDLE), C.POINTER(W.HANDLE), C.POINTER(_Security), W.DWORD], W.BOOL),
        "SetHandleInformation": ([W.HANDLE, W.DWORD, W.DWORD], W.BOOL),
        "CreateFileW": ([W.LPCWSTR, W.DWORD, W.DWORD, C.POINTER(_Security), W.DWORD, W.DWORD, W.HANDLE], W.HANDLE),
        "InitializeProcThreadAttributeList": ([C.c_void_p, W.DWORD, W.DWORD, C.POINTER(C.c_size_t)], W.BOOL),
        "UpdateProcThreadAttribute": ([C.c_void_p, W.DWORD, C.c_size_t, C.c_void_p, C.c_size_t, C.c_void_p, C.c_void_p], W.BOOL),
        "DeleteProcThreadAttributeList": ([C.c_void_p], None),
        "CreateProcessW": ([W.LPCWSTR, W.LPWSTR, C.c_void_p, C.c_void_p, W.BOOL, W.DWORD, C.c_void_p, W.LPCWSTR, C.c_void_p, C.POINTER(_Process)], W.BOOL),
        "AssignProcessToJobObject": ([W.HANDLE, W.HANDLE], W.BOOL),
        "ResumeThread": ([W.HANDLE], W.DWORD),
        "TerminateJobObject": ([W.HANDLE, W.UINT], W.BOOL),
        "TerminateProcess": ([W.HANDLE, W.UINT], W.BOOL),
        "WaitForSingleObject": ([W.HANDLE, W.DWORD], W.DWORD),
        "GetExitCodeProcess": ([W.HANDLE, C.POINTER(W.DWORD)], W.BOOL),
        "OpenProcess": ([W.DWORD, W.BOOL, W.DWORD], W.HANDLE),
        "CloseHandle": ([W.HANDLE], W.BOOL),
    }
    for name, (args, result) in signatures.items():
        fn = getattr(k, name)
        fn.argtypes, fn.restype = args, result
    return k


def run_bounded(argv, cwd, log_path, timeout_seconds, memory_limit_bytes,
                env=None, max_output_bytes=16*1024**2):
    """Run argv without a shell. Refuse existing logs and invalid input before work.

    Execution starts suspended; assignment failure kills the suspended process and
    raises rather than running without a job. A memory-limit notification means an
    allocation was refused; it does not prove a model ran within a chosen budget.
    Normal root exit preserves its exit code and kills any remaining job members.
    Exceptions also terminate the job; all waits and log draining are bounded.
    """
    if os.name != "nt":
        raise OSError("run_bounded requires Windows Job Objects")
    argv, cwd, log_path, child_env = _validate(argv, cwd, log_path, timeout_seconds,
                                             memory_limit_bytes, env, max_output_bytes)
    import msvcrt
    started = time.monotonic()
    k = _kernel()
    handles = []
    process = _Process()
    job = None
    reader = None
    read_fd = None
    attr = None
    assigned = False
    output_exceeded = threading.Event()
    reader_errors = []
    written = [0]

    def check(ok):
        if not ok:
            raise C.WinError(C.get_last_error())
        return ok

    def own(handle):
        if not handle or handle == C.c_void_p(-1).value:
            raise C.WinError(C.get_last_error())
        handles.append(handle)
        return handle

    def close(handle):
        check(k.CloseHandle(handle))
        handles.remove(handle)

    def query(kind, cls):
        value = cls()
        check(k.QueryInformationJobObject(job, kind, C.byref(value), C.sizeof(value), None))
        return value

    def memory_events():
        hit = False
        for _ in range(256):
            message, key, overlapped = W.DWORD(), C.c_size_t(), C.c_void_p()
            ok = k.GetQueuedCompletionStatus(port, C.byref(message), C.byref(key), C.byref(overlapped), 0)
            if not ok:
                if C.get_last_error() == 258:
                    break
                raise C.WinError(C.get_last_error())
            hit |= message.value == 10  # JOB_OBJECT_MSG_JOB_MEMORY_LIMIT
        return hit

    def has_live_members():
        # Accounting removal can lag the root's signaled process handle. Query
        # actual job members and their handles instead of labeling that lag an orphan.
        capacity = 32
        while capacity <= 131072:
            data = C.create_string_buffer(8 + capacity*C.sizeof(C.c_size_t))
            if k.QueryInformationJobObject(job, 3, data, len(data), None):
                count = W.DWORD.from_buffer(data, 4).value
                pids = (C.c_size_t * count).from_buffer(data, 8)
                for pid in pids:
                    handle = k.OpenProcess(0x100000, False, pid)
                    if not handle:
                        if C.get_last_error() == 87:  # already gone
                            continue
                        raise C.WinError(C.get_last_error())
                    try:
                        state = k.WaitForSingleObject(handle, 0)
                        if state == 258:
                            return True
                        if state != 0:
                            raise C.WinError(C.get_last_error())
                    finally:
                        k.CloseHandle(handle)
                return False
            if C.get_last_error() != 234:
                raise C.WinError(C.get_last_error())
            capacity *= 2
        raise RuntimeError("Job PID list exceeded safe query buffer")

    with log_path.open("xb", buffering=0) as log:
        try:
            job = own(k.CreateJobObjectW(None, None))
            limits = _ExtendedLimit()
            limits.BasicLimitInformation.LimitFlags = 0x200 | 0x2000  # JOB_MEMORY, KILL_ON_JOB_CLOSE
            limits.JobMemoryLimit = memory_limit_bytes
            check(k.SetInformationJobObject(job, 9, C.byref(limits), C.sizeof(limits)))
            port = own(k.CreateIoCompletionPort(W.HANDLE(-1), None, 0, 1))
            association = _Port(1, port)
            check(k.SetInformationJobObject(job, 7, C.byref(association), C.sizeof(association)))
            security = _Security(C.sizeof(_Security), None, True)
            pipe_read, pipe_write = W.HANDLE(), W.HANDLE()
            check(k.CreatePipe(C.byref(pipe_read), C.byref(pipe_write), C.byref(security), 0))
            own(pipe_read.value)
            own(pipe_write.value)
            check(k.SetHandleInformation(pipe_read, 1, 0))
            stdin = own(k.CreateFileW("NUL", 0x80000000, 3, C.byref(security), 3, 0, None))
            size = C.c_size_t()
            k.InitializeProcThreadAttributeList(None, 1, 0, C.byref(size))
            buffer = C.create_string_buffer(size.value)
            check(k.InitializeProcThreadAttributeList(buffer, 1, 0, C.byref(size)))
            attr = buffer
            inherited = (W.HANDLE * 2)(stdin, pipe_write.value)
            check(k.UpdateProcThreadAttribute(buffer, 0, 0x20002, inherited, C.sizeof(inherited), None, None))
            startup = _StartupEx()
            startup.StartupInfo.cb = C.sizeof(startup)
            startup.StartupInfo.dwFlags = 0x100 | 1
            startup.StartupInfo.wShowWindow = 0
            startup.StartupInfo.hStdInput = stdin
            startup.StartupInfo.hStdOutput = pipe_write.value
            startup.StartupInfo.hStdError = pipe_write.value
            startup.lpAttributeList = C.cast(buffer, C.c_void_p)
            environment = C.create_unicode_buffer('\0'.join(f"{key}={value}" for key, value in sorted(child_env.items(), key=lambda x: x[0].upper())) + '\0\0')
            command = C.create_unicode_buffer(subprocess.list2cmdline(argv))
            check(k.CreateProcessW(argv[0], command, None, None, True,
                                   4 | 0x08000000 | 0x00080000 | 0x400, environment,
                                   str(cwd), C.byref(startup), C.byref(process)))
            own(process.hProcess)
            own(process.hThread)
            check(k.AssignProcessToJobObject(job, process.hProcess))
            assigned = True
            close(pipe_write.value)
            close(stdin)
            read_fd = msvcrt.open_osfhandle(pipe_read.value, os.O_RDONLY | os.O_BINARY)
            handles.remove(pipe_read.value)  # fd now owns this handle

            def drain():
                try:
                    while chunk := os.read(read_fd, 65536):
                        remaining = max_output_bytes - written[0]
                        if remaining:
                            part = chunk[:remaining]
                            if log.write(part) != len(part):
                                raise OSError("Short log write")
                            written[0] += len(part)
                        if len(chunk) > remaining:
                            output_exceeded.set()
                except Exception as error:
                    reader_errors.append(error)
                finally:
                    os.close(read_fd)

            reader = threading.Thread(target=drain, daemon=True, name="bounded-output")
            reader.start()
            if k.ResumeThread(process.hThread) == 0xFFFFFFFF:
                raise C.WinError(C.get_last_error())
            close(process.hThread)
            termination = None
            memory_hit = False
            while True:
                memory_hit |= memory_events()
                root_done = k.WaitForSingleObject(process.hProcess, 0)
                if root_done not in (0, 258):
                    raise C.WinError(C.get_last_error())
                if memory_hit:
                    termination = "memory_limit"
                elif output_exceeded.is_set():
                    termination = "output_limit"
                elif reader_errors:
                    raise reader_errors[0]
                elif root_done == 0:
                    # The venv launcher can create a conhost that outlives its
                    # Python processes briefly. Give all members bounded natural
                    # teardown time; never exempt a process by name or PID.
                    grace_deadline = min(started + timeout_seconds, time.monotonic() + .1)
                    live = has_live_members()
                    while live and time.monotonic() < grace_deadline:
                        time.sleep(.005)
                        live = has_live_members()
                    if live:
                        termination = "orphan_cleanup"
                    break
                elif time.monotonic() - started >= timeout_seconds:
                    termination = "timeout"
                if termination:
                    break
                time.sleep(.01)
            # Save an already-finished root's status before terminating descendants.
            exit_code = W.DWORD()
            root_finished = k.WaitForSingleObject(process.hProcess, 0) == 0
            check(k.GetExitCodeProcess(process.hProcess, C.byref(exit_code)))
            root_exit = exit_code.value if root_finished else None
            check(k.TerminateJobObject(job, 1))
            cleanup_deadline = time.monotonic() + 2
            remaining = query(1, _Accounting).ActiveProcesses
            while remaining and time.monotonic() < cleanup_deadline:
                time.sleep(.01)
                remaining = query(1, _Accounting).ActiveProcesses
            memory_hit |= memory_events()
            peak = query(9, _ExtendedLimit).PeakJobMemoryUsed
            root_wait = k.WaitForSingleObject(process.hProcess, 100)
            check(k.GetExitCodeProcess(process.hProcess, C.byref(exit_code)))
            # Closing the only job handle is the final kill-on-close backstop.
            close(job)
            job = None
            reader.join(timeout=1)
            if reader_errors:
                raise reader_errors[0]
            if output_exceeded.is_set() and termination in (None, "orphan_cleanup"):
                termination = "output_limit"
            if memory_hit:
                termination = "memory_limit"
            result = BoundedResult(process.dwProcessId, root_exit if root_exit is not None else exit_code.value,
                                   0, termination,
                                   remaining == 0 and root_wait == 0 and not reader.is_alive(), remaining,
                                   peak, memory_limit_bytes, memory_hit, written[0], output_exceeded.is_set())
        finally:
            if attr is not None:
                k.DeleteProcThreadAttributeList(attr)
            if job:
                k.TerminateJobObject(job, 1)
            if process.hProcess and not assigned:
                k.TerminateProcess(process.hProcess, 1)  # still suspended; never ran unbounded
            if process.hProcess:
                k.WaitForSingleObject(process.hProcess, 2000)
            for handle in reversed(handles):
                k.CloseHandle(handle)
            if reader and reader.ident is not None:
                reader.join(timeout=1)
            elif read_fd is not None:
                os.close(read_fd)
    return replace(result, elapsed_seconds=round(time.monotonic() - started, 6))
