from __future__ import annotations
from dataclasses import dataclass, asdict
from typing import Any

@dataclass
class Resource:
    name: str
    kind: str
    os: str
    arch: str
    cpu_count: int
    memory_mb: int | None
    free_disk_mb: int | None
    gpu: str | None
    termux: bool
    owned: bool = True
    provider: str = "local"

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

@dataclass
class Decision:
    opportunity: str
    resource: str
    feasible: bool
    score: int
    mode: str
    reasons: list[str]
    warnings: list[str]
    next_actions: list[str]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
