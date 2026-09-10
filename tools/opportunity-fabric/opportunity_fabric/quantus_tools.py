from __future__ import annotations
import os
import platform
import subprocess
from pathlib import Path
from typing import Any

OFFICIAL_MINER_REPO = "https://github.com/Quantus-Network/quantus-miner.git"
ANDROID_TERMUX_MODE = "no-go-android-termux"
ANDROID_TERMUX_BLOCKERS = [
    "Quantus v1.0.1 native-node dependency graph locks rustix 1.1.2, which has a known Android linux_raw_sys compilation failure.",
    "The controlled Termux probe also exposed a protoc/Abseil ABI mismatch.",
    "Removing those blockers would not establish official Android support or sufficient phone-mining economics.",
]


def _version(cmd: list[str]) -> str | None:
    try:
        out = subprocess.check_output(cmd, stderr=subprocess.STDOUT, text=True, timeout=8)
        return out.strip().splitlines()[0] if out.strip() else "present"
    except Exception:
        return None


def _termux_arm64() -> bool:
    prefix = os.environ.get("PREFIX", "")
    termux = bool(os.environ.get("TERMUX_VERSION") or "/com.termux/" in prefix)
    arch = platform.machine().lower() or "unknown"
    return termux and arch in {"aarch64", "arm64"}


def source_preflight() -> dict[str, Any]:
    prefix = os.environ.get("PREFIX", "")
    termux = bool(os.environ.get("TERMUX_VERSION") or "/com.termux/" in prefix)
    arch = platform.machine().lower() or "unknown"
    tools = {
        "git": _version(["git", "--version"]),
        "rustc": _version(["rustc", "--version"]),
        "cargo": _version(["cargo", "--version"]),
        "clang": _version(["clang", "--version"]),
        "cmake": _version(["cmake", "--version"]),
        "pkg-config": _version(["pkg-config", "--version"]),
    }
    required = ["git", "rustc", "cargo", "clang", "cmake", "pkg-config"]
    missing = [k for k in required if not tools[k]]
    no_go = termux and arch in {"aarch64", "arm64"}
    notes = [
        "Quantus' official miner repository documents building the miner from source with Cargo.",
        "Source-build documentation is not the same as official Android/Termux support.",
        "The external miner also needs a compatible Quantus node and its miner auth token/TLS fingerprint before it can mine network jobs.",
    ]
    if no_go:
        notes = [
            "Opportunity Fabric has closed the Android/Termux ARM64 Quantus experiment as NO_GO.",
            "The recorded build failure occurred before node startup, sync, wallet/preimage creation, validation or mining.",
            "The phone remains useful as the Opportunity Fabric control plane; Quantus builds should not consume more phone resources.",
        ]
    return {
        "target": "quantus-miner-source",
        "official_repo": OFFICIAL_MINER_REPO,
        "os": platform.system().lower(),
        "arch": arch,
        "termux": termux,
        "official_prebuilt_arm64": False if termux and arch in {"aarch64", "arm64"} else None,
        "source_build_documented": True,
        "tools": tools,
        "missing_tools": missing,
        "ready_to_attempt_build": (not missing) and not no_go,
        "build_disabled": no_go,
        "decision": "NO_GO" if no_go else "UNDECIDED",
        "mode": ANDROID_TERMUX_MODE if no_go else "source-build-preflight",
        "blockers": ANDROID_TERMUX_BLOCKERS if no_go else [],
        "notes": notes,
    }


def build_plan(jobs: int = 2) -> dict[str, Any]:
    root = Path.home() / ".local" / "src" / "quantus-miner"
    binary = root / "target" / "release" / "quantus-miner"
    if _termux_arm64():
        return {
            "decision": "NO_GO",
            "mode": ANDROID_TERMUX_MODE,
            "build_disabled": True,
            "repo": OFFICIAL_MINER_REPO,
            "source_dir": str(root),
            "expected_binary": str(binary),
            "jobs": max(1, jobs),
            "commands": [],
            "blockers": ANDROID_TERMUX_BLOCKERS,
            "warning": "Quantus builds are disabled on Termux ARM64 by Opportunity Fabric policy; no compile or benchmark command will be produced.",
        }
    return {
        "decision": "UNDECIDED",
        "mode": "source-build-plan",
        "build_disabled": False,
        "repo": OFFICIAL_MINER_REPO,
        "source_dir": str(root),
        "expected_binary": str(binary),
        "jobs": max(1, jobs),
        "commands": [
            f"git clone --depth 1 {OFFICIAL_MINER_REPO} {root}",
            f"cd {root}",
            f"cargo build -p miner-cli --release -j {max(1, jobs)}",
            f"{binary} benchmark --cpu-workers 1 --duration 10",
        ],
        "warning": "This can take substantial time, storage, CPU and battery and should only be attempted on a supported/justified target.",
    }


def execute_build(jobs: int = 2) -> dict[str, Any]:
    if _termux_arm64():
        return {
            "ok": False,
            "executed": False,
            "stage": "policy-gate",
            "decision": "NO_GO",
            "mode": ANDROID_TERMUX_MODE,
            "build_disabled": True,
            "blockers": ANDROID_TERMUX_BLOCKERS,
            "message": "Quantus build execution is disabled on Termux ARM64. No clone, compile or benchmark was started.",
        }

    pf = source_preflight()
    if pf["missing_tools"]:
        return {"ok": False, "stage": "preflight", "preflight": pf}

    root = Path.home() / ".local" / "src" / "quantus-miner"
    root.parent.mkdir(parents=True, exist_ok=True)
    log_dir = Path.home() / ".local" / "state" / "opportunity-fabric" / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    log = log_dir / "quantus-miner-build.log"

    try:
        if not (root / ".git").is_dir():
            subprocess.run(["git", "clone", "--depth", "1", OFFICIAL_MINER_REPO, str(root)], check=True)
        else:
            subprocess.run(["git", "-C", str(root), "fetch", "--depth", "1", "origin", "main"], check=True)
            subprocess.run(["git", "-C", str(root), "reset", "--hard", "origin/main"], check=True)

        env = os.environ.copy()
        env.setdefault("CARGO_BUILD_JOBS", str(max(1, jobs)))
        with log.open("w", encoding="utf-8") as fh:
            proc = subprocess.run(
                ["cargo", "build", "-p", "miner-cli", "--release", "-j", str(max(1, jobs))],
                cwd=root,
                env=env,
                stdout=fh,
                stderr=subprocess.STDOUT,
                text=True,
            )
        binary = root / "target" / "release" / "quantus-miner"
        if proc.returncode != 0 or not binary.exists():
            tail = ""
            try:
                tail = "\n".join(log.read_text(encoding="utf-8", errors="replace").splitlines()[-80:])
            except Exception:
                pass
            return {"ok": False, "stage": "cargo-build", "returncode": proc.returncode, "log": str(log), "log_tail": tail}

        bench = subprocess.run(
            [str(binary), "benchmark", "--cpu-workers", "1", "--duration", "10"],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=90,
        )
        return {
            "ok": bench.returncode == 0,
            "stage": "benchmark",
            "binary": str(binary),
            "build_log": str(log),
            "benchmark_returncode": bench.returncode,
            "benchmark_output": bench.stdout[-12000:],
        }
    except Exception as exc:
        return {"ok": False, "stage": "exception", "error": f"{type(exc).__name__}: {exc}", "log": str(log)}
