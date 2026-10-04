# Windows bounded process supervisor QA — 2026-10-04

이 문서는 `tools/research/bounded_process.py`의 **합성 Python 프로세스 실행·제한·정리만** 검증한다. 최종 Windows venv 검사 **10통과/0실패, 2.235초**, 같은 소스의 base Python 검사 **9통과/0실패/launcher 전용1제외, 2.060초**다. 부모 작업자의 독립 venv 회귀도 **10통과/0실패, 2.246초**다. 전체 저장소·Android QA, 모델 검사·추론 성공, 제품 성능·정확도 또는 배포 완료의 증거가 아니다.

## 원인과 수정

보존된 `work/speech-maturity-checkpoint-audit/run.py`는 `Popen.pid` 한 개의 RSS/`peak_wset`을 검사하고 `process.kill()`로 그 PID만 종료한다. Windows venv의 `python.exe`는 launcher이며 실제 Python/torch 작업은 다른 PID에서 수행된다. 이 파일은 읽기만 했고 수정·재실행하지 않았다.

최종 합성 48MiB 할당에서 launcher PID37448의 RSS는 **4,321,280 bytes**, 실제 Python PID31128의 RSS는 **68,898,816 bytes**, Job의 raw committed peak는 **63,246,336 bytes**였다. root의 RSS만 관찰하면 자식의 메모리를 놓친다는 회귀를 동적으로 확인했다. 이 수치는 합성 실행이며 과거 모델 검사의 RAM 실측을 복구하지 않는다.

실행기는 `CreateProcessW(CREATE_SUSPENDED | CREATE_NO_WINDOW)`로 root를 창 없이 생성하고, aggregate committed-memory hard limit와 `KILL_ON_JOB_CLOSE`를 설정한 Job Object에 **assign한 뒤에만 resume**한다. assign이 실패하면 suspended root를 종료하고 예외로 끝낸다. breakaway를 허용하지 않으므로 launcher의 자식·손자도 같은 Job에 포함된다. `STARTUPINFOEX`의 handle list로 자식에 전달할 handle을 stdout/stderr pipe와 NUL stdin으로 제한한다. shell을 거치지 않으며 argv를 Windows quoting 규칙으로 전달한다.

completion-port 계측에서 venv launcher가 실제 Python과 함께 **conhost.exe**도 생성함을 확인했다. Python root가 정상 종료한 직후 conhost가 잠깐 살아 있는 경우가 있어 정상 실행을 고아 정리로 오분류했다. 최종 구현은 root 종료 뒤 기존 execution deadline 안에서 **최대100ms**의 자연 종료를 기다린다. 남은 Job 멤버는 이름에 따른 예외 없이 모두 종료한다. Job PID 목록의 process handle 상태로 살아 있는 멤버를 확인하며, 정리는 Job `ActiveProcesses==0`, signaled root handle, log reader 종료를 함께 확인한다. 테스트는 Windows PID 목록 제거 지연을 고려해 최대0.5초의 조건 대기를 하고 실제 PID 부재까지 검사한다.

## API와 통과 조건

```python
run_bounded(argv, cwd, log_path, timeout_seconds, memory_limit_bytes,
            env=None, max_output_bytes=16*1024**2) -> BoundedResult
```

기존 log는 `xb`로 열어 덮어쓰기를 거부한다. argv/cwd/환경 변수·시간·메모리·출력 한도를 작업 시작 전에 검사한다. stdout과 stderr를 하나의 binary log로 저장하며 출력 상한을 넘으면 정확히 상한까지만 쓰고 Job을 종료한다. import만으로 프로세스를 시작하거나 Job/pipe를 만들지 않는다.

결과는 root PID, root exit code, 최종 cleanup까지 포함한 elapsed, `termination`, `cleanup_complete`, 남은 Job 프로세스 수, raw `peak_job_committed_bytes`, 설정 메모리 한도, kernel memory-limit event 유무, log byte 수와 truncation 유무를 반환한다. `termination`은 정상 `None`, `timeout`, `memory_limit`, `output_limit`, `orphan_cleanup` 중 하나다. root가 이미 끝났다면 그 exit code를 보존하고 나머지 자식만 정리한다. 진행 중인 root를 Job 제한으로 종료한 경우 종료 코드1을 반환한다. native setup/파일/검증 실패는 정리 후 예외로 전달한다.

모델 검사·추론 호출자의 최소 실행 통과 조건은 다음과 같다. 이후 결과 파일의 완전성·내용 검증은 호출자가 별도로 수행한다.

```python
execution_passed = (
    result.exit_code == 0
    and result.termination is None
    and result.cleanup_complete
    and not result.output_truncated
)
```

**2GiB 또는3GiB를 설정했다는 사실만으로 게이트 통과를 보고하지 않는다.** 종료·정리·출력·실제 결과를 함께 확인한다. `cleanup_complete=False` 또는 종료 이유가 있으면 실행을 성공으로 보고하지 않는다.

## 메모리 지표의 해석

`JOB_OBJECT_LIMIT_JOB_MEMORY`는 Job 전체의 **committed virtual memory**를 제한한다. 물리 RAM RSS/working set과 다르며 이 실행기는 RSS 실측값을 반환하지 않는다. `peak_job_committed_bytes`는 `QueryInformationJobObject(JobObjectExtendedLimitInformation).PeakJobMemoryUsed`의 raw Windows 값이며 샘플링한 root RSS가 아니다.

실제로 **96MiB(100,663,296 bytes)** 한도에서256MiB를 요청했을 때 kernel의 `JOB_OBJECT_MSG_JOB_MEMORY_LIMIT`가 발생했고 `ALLOCATED` 성공 표시는 나오지 않았다. root exit1, `termination=memory_limit`, Job active0 정리가 확인됐다. 이때 Windows raw peak는 **276,766,720 bytes**로 상한보다 높았다. 거부된 할당 요청이 raw peak에 잡힐 수 있으므로 `peak<=limit` 비교를 통과 조건으로 사용하지 않는다. **상한보다 높은 raw peak 자체는 그만큼의 실제 resident allocation이 허용됐다는 증거가 아니다.**

aggregate 검사에서는 먼저 한 자식이48MiB를 실제 할당하고 성공 표시·ready 파일을 남긴 뒤 다른 자식이48MiB를 요청하도록 순서를 고정했다. **110MiB(115,343,360 bytes)** 한도에서 두 자식의 PID가 관찰됐고 성공 표시는 한 번뿐이었다. kernel limit event, `termination=memory_limit`, root exit1, 모든 PID 부재가 확인됐다. raw peak는 **128,184,320 bytes**였다. 처음120MiB 설정은 실제 요구량이 상한 미만이라 거부되지 않았으며, 그 실행을 통과로 처리하지 않고 실패 증거를 보존했다.

memory-limit completion notification은 Windows의 Job event다. `memory_limit_hit=True`는 kernel 이벤트를 관찰했다는 뜻이며, 이벤트 미관찰만으로 모든 할당이 성공했다거나 모델이 한도 안에서 정상 완료됐다고 증명하지 않는다. 실제 성공·exit/termination/cleanup/결과 검증이 필요하다.

## UltraQA 시나리오 행렬

모든 입력은 작은 로컬 합성 코드이며 원본 미디어·모델을 읽지 않는다. 정상 외의 실패 시나리오는 잘못된 caller 설정, 고아 자식, 무한 대기, 메모리·로그 과다 사용을 가정한다. `test_bounded_process.py`가 실행 하네스다.

| ID | 의도 / 모델 | 설정·명령 | 기대 신호 | 최종 결과·수정 | 증거 / 정리 |
|---|---|---|---|---|---|
| N1 | 정상 실행 | `print('normal-output')` | exit0, 정확한 출력, termination 없음 | 통과; conhost teardown 분류 수정 | `test_supervisor_exists.../`; active0·root PID 부재 |
| E1 | 성공 문구로 실패를 감추는 실행 | stdout `SUCCESS`, stderr `failure`, exit7 | 두 출력·exit7 보존 | 통과 | `test_misleading_success.../`; active0·root PID 부재 |
| T1 | launcher·자식·손자 대기 | 3단계 Python, 각8초 sleep, 1초 제한 | timeout, 관찰 PID3개 이상 모두 종료 | 통과 | `test_timeout.../`; active0·PID 부재 |
| O1 | 정상 root 뒤 고아·stdout handle 잔류 | child8초 sleep, root0.3초 뒤 exit | root exit0 보존, orphan_cleanup | 통과 | `test_normal_root_exit.../`; pipe reader 종료·PID 부재 |
| M1 | 큰 자식 할당 | 256MiB 요청, Job96MiB | kernel memory event, 성공 마커 없음 | 통과; peak 비교 가정 수정 | `test_hard_memory_limit.../`; active0·PID 부재 |
| M2 | 여러 자식의 합계 할당 | 순차48MiB 두 요청, Job110MiB | 첫 성공·두 번째 거부·kernel event | 통과; 첫120MiB 설정 미거부와 순서 race 수정 | `test_aggregate_limit.../`; active0·두 PID 부재 |
| R1 | launcher RSS 측정 오류 | 자식48MiB, 부모·자식 RSS 비교 | launcher PID와 실제 PID 분리·RSS 차이 | venv 통과; base에서는 해당 조건이 없어 명시 제외 | `test_launcher_rss.../`; 두 PID 부재 |
| L1 | 출력 flood | 1,000,000 bytes 출력, disk bound4096 | 정확히4096 bytes, output_limit | 통과 | `test_output_flood.../`; active0·PID 부재 |
| V1 | 충돌/잘못된 설정 | 기존 log, 빈/string argv, NUL, 0/NaN 시간, 음수/float 메모리, 출력0, 없는 cwd, 잘못된 env | child marker·invalid log 생성 전 거부, 기존 log 보존 | 통과 | `test_log_collision.../`; child 작업 없음 |
| A1 | shell 문자열·Unicode quoting | 공백·한글 script 경로, `; & "quote"` argv, explicit env | 문자 그대로 전달·환경 값 보존 | 통과 | `test_argv_quoting.../`; root PID 부재 |

최종 근거 경로의 공통 prefix는 ignored `work/bounded-process-qa/frozen-venv-2/`다. 전체 test 함수 이름으로 하위 디렉터리가 생성되고 각 `output.log`/`result.json`에 결과가 남는다. A1은 `quoted.log`, V1은 보존 여부를 검사한 `occupied.log`다.

## 실행 명령과 증거

repo worktree에서 venv Python3.13.14를 사용했다. 신규 패키지를 설치하지 않았다. production 실행기는 stdlib만 사용하며 테스트 PID/RSS 검사에 기존 `psutil`을 사용한다.

```powershell
$env:BOUNDED_QA_DIR = (Join-Path (Get-Location) 'work/bounded-process-qa/frozen-venv-2')
& 'work/ast-portability/venv/Scripts/python.exe' -m unittest discover -s tools/research -p test_bounded_process.py -v
```

증거 디렉터리는 반복 실행 시 새 이름을 사용해야 한다. fixture와 log 충돌을 의도적으로 거부한다. base 검사에서는 동일 Python3.13.14의 base executable을 사용하고 기존 venv `Lib/site-packages`를 `PYTHONPATH`에 연결해 `psutil`만 재사용했다. launcher 전용 R1을 제외한9개를 검증했다.

| 증거 | exit / 시간 | 의미 |
|---|---|---|
| `work/bounded-process-qa/red.txt` | 1 / 0.002초 | 구현 전 missing API assertion RED; traceback에 실패 이유 명시 |
| `green-1.txt` | 1 / 4.783초 | kernel memory 이벤트 확인;120MiB 미거부·raw peak 상한 비교의 틀린 기대 발견 |
| `green-2.txt` | 1 / 1.850초 | 메모리 검사 통과; 종료 분류 race 발견 |
| `green-3.txt` | 0 / 1.902초 | 당시9개 통과, 이후 검사 확장·후속 race를 별도로 기록 |
| `final-venv.txt` | 1 / 2.007초 | accounting0 직후 PID 목록 제거 지연을 발견 |
| `final-venv-2.txt` | 0 / 2.372초 | PID 조건 대기·aggregate 순서 고정 후10개 통과 |
| `frozen-venv.txt` | 1 / 2.095초 | 최종 정리 변경 후 정상 exit 분류 재현; 이 실패를 완료로 보고하지 않음 |
| `trace-native-3/trace.json` | 0 | native Job NEW_PROCESS 계측에서 launcher 자식 conhost의 생성·종료 확인 |
| **`frozen-venv-2.txt`** | **0 / 2.235초** | conhost grace를 포함한 최종 소스10통과/0실패 |
| **`frozen-base.txt`** | **0 / 2.060초** | 같은 최종 소스9통과/launcher 전용1제외 |
| **`parent-review-1.txt`** | **0 / 2.246초** | 부모 작업자의 독립 최종 venv10통과/0실패 |
| `state-transcript.json` | 각 CLI exit0 | scope 전용 OMX state write/read/clear, 마지막 `exists:false` 확인 |

각 실행은2~5초 안에 끝났고 정상 실행·제한·정리의 결과를 함께 확인했다. 기존 연구 도구 전체 suite나 Gradle/ADB/제품 QA는 실행 범위에서 제외했다. 모델·원본에 접근하지 말라는 작업 경계를 유지했다.

## 정리와 한계

최종 검사는 root와 관찰한 모든 실제 Python PID의 부재, Job active0, log reader 종료를 검증했다. evidence는 ignored `work/bounded-process-qa/`에 합성 fixture/log/result로 남겼다. 임시 OMX 상태는 같은 디렉터리의 별도 `state-session`에서만 만들고 clear 뒤 부재를 확인했으며 저장소의 기존 `.omx` 상태는 변경하지 않았다. 초기 RED/진단은 CLI lifecycle 기록보다 먼저 수행됐다는 절차 차이도 state transcript의 note에 남겼다. 첫 OMX read 하네스는 응답의 `success` 필드를 잘못 가정해 실패했으며 실제 read/clear schema로 수정하고 상태를 제거했다.

이 작업은3개 소유 파일과 ignored 합성 증거만 추가했다. 제품 Android·환경·모델·원본·보존된 oneoff 실행기를 변경하지 않았고 commit/push/ADB/Gradle을 하지 않았다. 다른 작업자의 변경은 되돌리지 않았다.

Job Object는 신뢰하는 로컬 연구 명령의 실행 자원 경계이며 악성 코드용 sandbox가 아니다. kill-on-close는 native 설정·handle lifecycle로 구현했고 정상 종료·timeout·memory event 정리를 동적으로 검증했다. **supervisor 자체의 강제 종료/호스트 재부팅은 별도의 동적 검사로 수행하지 않았다.** GPU/VRAM, CPU/IO·네트워크·파일 접근 권한은 제한하지 않는다. 비호환 nested Job 정책에서는 assign을 실패로 처리하므로 caller는 이를 성공 실행으로 처리하면 안 된다.

execution timeout은 작업 시작부터 제한하며 정리에는 별도로 유한 wait(최대2초의 Job 종료 확인, reader join 등)가 필요할 수 있다. 반환 elapsed에는 최종 handle 정리와 wait까지 포함된다. 이 실행기 검증으로 실제 모델의2GiB/3GiB 통과, 모바일 RAM·속도 또는 모델 품질을 주장하지 않는다.
