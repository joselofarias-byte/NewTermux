# Quantus on Adreno 720 / native Termux

Validated on Android/aarch64 with `Adreno (TM) 720` through Vulkan/WGPU.

## Observed benchmark results

- CPU, 4 workers: ~354.49 KH/s (10 s), stable.
- GPU batch 25,000: ~85.79 KH/s (10 s), stable.
- GPU batch 50,000: ~85.84 KH/s (10 s), stable.
- GPU batch 100,000: ~86.30 KH/s (10 s), stable.
- GPU batch 100,000 burn-in: ~86.33 KH/s average over 20.85 s, 1,800,000 hashes, stable; no WGPU timeout, device loss, or panic.
- GPU batch 250,000: unstable. WGPU timed out mapping the GPU buffer after 30 s, marked the device lost/unresponsive, then panicked while waiting for the last successful submission.

GPU identification from the miner:

- Adapter: `Adreno (TM) 720`
- Type/backend: `IntegratedGpu, Vulkan`
- Qualcomm vendor: `0x5143`
- Device: `1124204544`
- Current upstream tier: `Qualcomm Adreno (Unknown)`
- Fallback workgroups reported: `2730`

## Operational profile

Use `--gpu-batch-size 100000` or lower on this device. For long-running `serve`, start with `--gpu-throttle-ms 50` and keep CPU worker count explicit so thermal load is intentional.

The repository helper `scripts/quantus-adreno720-safe.sh` enforces the validated 100k ceiling by default, performs a short GPU check in a fresh process, and can then enter `serve` mode when node credential file paths are supplied.

### Check only

```bash
bash scripts/quantus-adreno720-safe.sh check
```

### Serve

```bash
MINER_NODE_ADDR=127.0.0.1:9833 \
MINER_AUTH_TOKEN_FILE=/path/to/miner-auth-token \
MINER_TLS_CERT_SHA256_FILE=/path/to/miner-tls-cert-sha256 \
bash scripts/quantus-adreno720-safe.sh serve
```

Default serve profile:

- GPU devices: 1
- GPU batch: 100000
- GPU throttle: 50 ms
- CPU workers: 0
- metrics: port 9900

Override CPU workers intentionally, e.g. `QUANTUS_CPU_WORKERS=2`, only after checking temperature and battery/charger behavior. The 4-worker CPU benchmark was substantially faster than the Adreno 720 GPU benchmark, but combined CPU+GPU load is also much more thermally demanding.

## Node path

The external miner requires a Quantus validator node. Current upstream documentation uses `--miner-listen-port 9833`; the node creates `miner-auth-token` and `miner-tls-cert-sha256` under `<base-path>/chains/<chain>/`, and the external miner should read those files rather than putting secrets on the command line.

As of Quantus `v1.0.1`, upstream publishes an official `aarch64-unknown-linux-gnu` node binary. Native Android/Termux is not an advertised platform, so `scripts/quantus-node-arm64-glibc-preflight.sh` performs a non-destructive compatibility check via Termux `glibc-runner`: it verifies the official release digest and runs only `quantus-node --version`. It does not generate keys, start a validator, sync, or mine.

## Upstream status

The current official miner README documents file-based node auth (`miner-auth-token`) and TLS certificate fingerprint pinning (`miner-tls-cert-sha256`). It also exposes `--gpu-batch-size` and `--gpu-throttle-ms` for `serve`.

An attempt to open an upstream issue from the connected GitHub integration failed with HTTP 403 because the integration lacks write permission to `Quantus-Network/quantus-miner`. The reproducible result is therefore retained here.
