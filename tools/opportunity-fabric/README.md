# Opportunity Fabric v0.2.2

Orquestador **zero-cost-first** para encontrar y ejecutar oportunidades de cómputo legítimas sin violar términos de plataformas. Primer plugin: **Quantus**.

## Objetivo

- usar hardware propio y recursos gratuitos **solo donde esté permitido**;
- detectar automáticamente arquitectura/CPU/RAM/disco/GPU/Termux;
- evaluar si una oportunidad realmente puede ejecutarse en ese recurso;
- bloquear proveedores donde cryptomining esté prohibido o no verificado;
- mantener wallet/seed fuera del orquestador;
- crecer hacia testnets incentivadas, nodos, storage/proving y otros trabajos distribuídos cuando tengan mejor expectativa que el costo de ejecución.

## Arranque en Termux

```bash
bash scripts/install-opportunity-fabric.sh
of status
of evaluate quantus
of quantus-preflight
```

## Quantus en Android/Termux ARM64: NO_GO

El experimento de Quantus en Android/Termux ARM64 queda cerrado como **NO_GO** en v0.2.2.

El build controlado del nodo oficial v1.0.1 alcanzó la dependencia `rustix 1.1.2` y quedó bloqueado por el fallo conocido de Android con referencias `linux_raw_sys`. El mismo probe detectó además una incompatibilidad local `protoc`/Abseil. Ninguno de esos intentos inició nodo, sincronización, wallet/preimage, validación ni minería.

Aunque `rustix 1.1.3` ha sido reportado como corrección del error Android, resolver el blocker de compilación no demuestra soporte oficial de Quantus en Android ni vuelve atractiva la economía de minería CPU en teléfono. Por eso Opportunity Fabric no consume más CPU, batería, almacenamiento ni tiempo de compilación en este camino.

En Termux ARM64:

```bash
of evaluate quantus
```

debe devolver `feasible: false`, `score: 0` y `mode: no-go-android-termux`.

```bash
of quantus-build-plan
```

debe devolver `build_disabled: true` y una lista de comandos vacía.

Incluso:

```bash
of quantus-build --execute
```

queda protegido por un `policy-gate`: no clona, no compila y no ejecuta benchmark en Termux ARM64.

Quantus solo debería reabrirse para Android si cambia materialmente el upstream: por ejemplo, aparece una ruta ARM64/Android oficialmente soportada **y** la economía justifica un benchmark nuevo.

## Política zero-cost

El proyecto no intenta evadir límites ni políticas. Colab, Kaggle y GitHub Actions quedan bloqueados para minería. OCI Free se excluye conservadoramente por riesgo de suspensión por coin-mining. Otros clouds quedan en `review` hasta verificar permiso explícito.

```bash
of policies
```

El teléfono puede seguir siendo el **control plane** de Opportunity Fabric mientras el sistema prioriza oportunidades con mejor relación entre expectativa, costo, consumo y riesgo.
