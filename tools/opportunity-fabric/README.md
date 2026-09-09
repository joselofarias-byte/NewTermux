# Opportunity Fabric v0.2.0

Orquestador **zero-cost-first** para encontrar y ejecutar oportunidades de cómputo legítimas sin violar términos de plataformas. Primer plugin: **Quantus**.

## Objetivo

- usar hardware propio y recursos gratuitos **solo donde esté permitido**;
- detectar automáticamente arquitectura/CPU/RAM/disco/GPU/Termux;
- evaluar si una oportunidad realmente puede ejecutarse en ese recurso;
- bloquear proveedores donde cryptomining esté prohibido o no verificado;
- mantener wallet/seed fuera del orquestador;
- crecer después hacia testnets incentivadas, nodos, storage/proving y otros trabajos distribuídos.

## Arranque en Termux

```bash
cd opportunity-fabric-v0.2.0
bash scripts/install-termux.sh
of status
of evaluate quantus
```

En Android/Termux ARM64 el binario precompilado oficial no existe, pero el repositorio oficial publica una ruta de compilación desde fuente con Cargo. Por eso v0.2 cambia el estado a **experimental-source-build**: es una vía para probar, no soporte oficial de Android.

## Quantus hoy

La web y el whitepaper v0.4.1 fijan el génesis de mainnet para **9/9/2026**. El minero oficial existe y Quantus recomienda GPU. La guía pública de minería capturada hoy aún conserva ejemplos de `planck`; este proyecto no reutiliza esos comandos a ciegas para mainnet.

En Termux:

```bash
of quantus-preflight
of quantus-build-plan
```

Solo después de un preflight correcto se debe intentar una compilación controlada y un benchmark corto.

## Política zero-cost

El proyecto no intenta evadir límites ni políticas. Colab, Kaggle y GitHub Actions quedan bloqueados para minería. OCI Free se excluye conservadoramente por riesgo real de suspensión por coin-mining. Otros clouds quedan en `review` hasta verificar permiso explícito.

```bash
of policies
```

## v0.2 — Quantus ARM64 experimental path

`of quantus-preflight` comprueba el toolchain nativo/Rust.
`of quantus-build-plan` muestra la compilación exacta prevista.
`of quantus-build --execute` clona el repositorio oficial de Quantus Miner, compila `miner-cli` en release y ejecuta un benchmark CPU de 1 worker durante 10 segundos.

ARM64/Termux sigue siendo experimental: que el código fuente compile no equivale a soporte oficial de Android.
