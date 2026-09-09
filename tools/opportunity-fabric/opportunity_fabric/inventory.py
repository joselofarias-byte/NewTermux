from __future__ import annotations
import os, platform, shutil, subprocess
from .models import Resource

def _mem_mb() -> int | None:
    try:
        with open('/proc/meminfo', 'r', encoding='utf-8') as f:
            for line in f:
                if line.startswith('MemTotal:'):
                    return int(line.split()[1]) // 1024
    except Exception:
        return None
    return None

def _gpu() -> str | None:
    probes = [
        ['nvidia-smi', '--query-gpu=name', '--format=csv,noheader'],
        ['sh', '-lc', "command -v lspci >/dev/null 2>&1 && lspci | grep -Ei 'vga|3d|display' | head -n 2"],
    ]
    for cmd in probes:
        try:
            out = subprocess.check_output(cmd, stderr=subprocess.DEVNULL, text=True, timeout=3).strip()
            if out:
                return out.replace('\n', '; ')
        except Exception:
            pass
    return None

def detect_local() -> Resource:
    termux = bool(os.environ.get('TERMUX_VERSION') or '/com.termux/' in os.environ.get('PREFIX',''))
    sysname = platform.system().lower() or 'unknown'
    arch = platform.machine().lower() or 'unknown'
    try:
        disk = shutil.disk_usage(os.path.expanduser('~')).free // (1024*1024)
    except Exception:
        disk = None
    return Resource(
        name='local', kind='device', os=sysname, arch=arch,
        cpu_count=os.cpu_count() or 1, memory_mb=_mem_mb(), free_disk_mb=disk,
        gpu=_gpu(), termux=termux, owned=True, provider='local'
    )
