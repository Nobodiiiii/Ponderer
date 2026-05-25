#!/usr/bin/env python3
"""30s smoke test: launch both instances, monitor logs, kill after 30s."""
import io, os, subprocess, sys, time

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.abspath(__file__))
FABRIC_LOG = os.path.join(ROOT, ".minecraft/versions/1.20.1-Fabric/logs/latest.log")
FORGE_LOG = os.path.join(ROOT, ".minecraft/versions/1.20.1-Forge/logs/latest.log")
SMOKE_SECONDS = 30

def java_pids():
    try:
        out = subprocess.check_output(
            ["wmic", "process", "where", "name='javaw.exe' or name='java.exe'", "get", "ProcessId"],
            stderr=subprocess.DEVNULL,
        ).decode(errors="replace")
        return {int(line.strip()) for line in out.splitlines() if line.strip().isdigit()}
    except Exception:
        return set()

pre = java_pids()
print(f"[SMOKE] pre-existing java pids: {sorted(pre)}")

launcher = subprocess.Popen(
    [sys.executable, os.path.join(ROOT, "test.py"), "both", "--no-copy"],
    cwd=ROOT,
    stdout=subprocess.DEVNULL,
    stderr=subprocess.STDOUT,
)
print(f"[SMOKE] launcher pid={launcher.pid}, waiting {SMOKE_SECONDS}s ...")

t0 = time.time()
while time.time() - t0 < SMOKE_SECONDS:
    time.sleep(2)
    elapsed = int(time.time() - t0)
    fab_sz = os.path.getsize(FABRIC_LOG) if os.path.exists(FABRIC_LOG) else 0
    fwg_sz = os.path.getsize(FORGE_LOG) if os.path.exists(FORGE_LOG) else 0
    new_pids = java_pids() - pre
    print(f"  +{elapsed:02d}s  fabric_log={fab_sz}B  forge_log={fwg_sz}B  new_java_pids={sorted(new_pids)}")

to_kill = java_pids() - pre
print(f"[SMOKE] killing pids: {sorted(to_kill)}")
for pid in to_kill:
    subprocess.run(["taskkill", "/F", "/PID", str(pid)], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
try:
    launcher.terminate()
except Exception:
    pass

print(f"[SMOKE] done; fabric log={os.path.getsize(FABRIC_LOG)}B, forge log={os.path.getsize(FORGE_LOG)}B")
