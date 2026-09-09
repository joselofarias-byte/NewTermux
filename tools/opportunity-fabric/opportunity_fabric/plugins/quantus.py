from __future__ import annotations
from .base import OpportunityPlugin
from ..models import Resource, Decision
from ..policy import mining_allowed

class QuantusPlugin(OpportunityPlugin):
    slug = 'quantus'
    title = 'Quantus QTC/QPoW'

    def evaluate(self, r: Resource) -> Decision:
        reasons, warnings, actions = [], [], []
        allowed, policy_reason = mining_allowed(r.provider)
        if not allowed:
            return Decision(self.slug, r.name, False, 0, 'blocked-by-policy', [], [policy_reason], [])

        os_ok = r.os in {'linux', 'darwin', 'android'}
        x86 = r.arch in {'x86_64', 'amd64'}
        arm64 = r.arch in {'arm64', 'aarch64'}
        mac_arm = r.os == 'darwin' and arm64

        if r.termux and arm64:
            # v0.2 correction: lack of an official ARM64 release != lack of a source-build path.
            reasons += [
                'The official quantus-miner repository publishes a Cargo source-build path (cargo build -p miner-cli --release).',
                'This phone has enough CPU/RAM/storage to justify a controlled ARM64 source-build experiment.',
                'The phone can remain the Opportunity Fabric control plane even if the miner build is not viable.',
            ]
            warnings += [
                'Quantus does not publish an official native Linux ARM64/Termux miner release; Android ARM64 remains an experimental source-build target.',
                'A successful miner build alone is not enough: external mining requires a compatible Quantus node plus its auth-token and TLS fingerprint.',
                'Phone CPU mining may be thermally constrained and much slower than a desktop GPU; benchmark before any long-running use.',
            ]
            actions += [
                'Run `of quantus-preflight` to verify the local Rust/native build toolchain.',
                'If preflight passes, run a controlled source build and 1-worker benchmark before deciding whether phone mining is worthwhile.',
                'Keep secrets outside shell history and keep the miner-node channel private.',
            ]
            score = 38
            if r.memory_mb and r.memory_mb >= 8192:
                score += 5
            if r.free_disk_mb and r.free_disk_mb >= 50000:
                score += 5
            return Decision(self.slug, r.name, True, min(score, 55), 'experimental-source-build', reasons, warnings, actions)

        if not (os_ok and (x86 or mac_arm or arm64)):
            warnings.append(f'Platform {r.os}/{r.arch} is not in the documented/native or experimental source-build path.')
            return Decision(self.slug, r.name, False, 10, 'unsupported', reasons, warnings, actions)

        if x86 or mac_arm:
            reasons.append('Platform matches the documented native Quantus mining path.')
            score = 55
            mode = 'native-miner'
        else:
            reasons.append('Source build may be possible, but this platform is not an official prebuilt miner target.')
            score = 35
            mode = 'experimental-source-build'

        if r.gpu:
            score += 25
            reasons.append('GPU detected; Quantus recommends GPU mining and documents much higher throughput than CPU-only mining.')
        else:
            reasons.append('No GPU detected; CPU mining is possible but expected to be much slower.')
        if r.memory_mb and r.memory_mb >= 4096:
            score += 5
        if r.free_disk_mb and r.free_disk_mb >= 10240:
            score += 5
        warnings += [
            'Use only the current official mainnet chain/release pair; do not blindly reuse older Planck-testnet commands.',
            'Never expose the miner service publicly. Keep miner traffic private and protect auth-token/TLS pin files.',
            'Back up wallet seed material offline. Do not put seed phrases in shell history, environment files, logs or this orchestrator.',
        ]
        actions += [
            'Install or build matching official quantus-node and quantus-miner versions.',
            'Create/use a Quantus wormhole reward address.',
            'Benchmark CPU/GPU locally before long-running mining.',
            'Confirm the current mainnet chain identifier and release pair from official sources before start.',
        ]
        return Decision(self.slug, r.name, True, min(score, 100), mode, reasons, warnings, actions)
