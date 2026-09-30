# NewTermux — desacople de Google Play

Fecha: 2026-09-29

## Objetivo

Desacoplar NewTermux de Google Play como canal de instalación, firma,
versionado, actualización y distribución sin romper el entorno ya validado
en el HONOR 200.

## Hallazgo principal

La cadena operativa actual no depende de Google Play Services ni de
`com.android.vending`. El acople restante es de identidad/entrega:
la build física usa `applicationId=com.termux`, flavor `playcompat`,
bootstrap con PREFIX `/data/data/com.termux/files/usr` y workflows
pensados alrededor de la migración desde Termux Play.

Cambiar el applicationId sin reconstruir el ecosistema de PREFIX repetiría
el fallo ya observado con `com.newtermux.dev`.

## Fase A — distribución independiente

Esta rama introduce el flavor `direct`.

`direct`:
- mantiene temporalmente `applicationId=com.termux`;
- mantiene el bootstrap stock compatible con el PREFIX restaurado;
- no representa un canal de Google Play;
- se compila y entrega desde GitHub Actions;
- usa nombres/versiones NewTermux propios.

`playcompat` queda temporalmente como compatibilidad/rollback mientras se
valida físicamente `direct`.

### Firma

La prueba CI usa la firma debug actual sólo para validar que el flavor
direct compila y funciona. No debe publicarse como release estable.

Antes de habilitar releases permanentes:
1. crear una clave de firma propia de NewTermux;
2. guardarla exclusivamente como secret de GitHub Actions;
3. fijar y verificar el SHA-256 del certificado en CI;
4. conservar esa misma clave para todas las actualizaciones futuras.

### Canal de actualización

La entrega estable debe provenir del repositorio NewTermux:
- GitHub Releases;
- APK ARM64 firmado;
- SHA-256 publicado;
- actualización in-place verificada;
- opcionalmente, comprobador interno de nuevas versiones contra Releases.

Google Play no debe ser necesario para instalar, actualizar ni recuperar
NewTermux.

## Fase B — independencia total del package ID

La independencia completa exige dejar de usar también `com.termux`.
Eso no es un simple rename.

Requiere:
- package ID propio definitivo;
- bootstrap reconstruido con el nuevo TERMUX_APP_PACKAGE;
- paquetes que embeben PREFIX reconstruidos o validados;
- authorities/permisos/intents/plugins migrados;
- migración TBM del HOME/PREFIX;
- pruebas de login, sh, bash, pkg, proot y CLIs;
- reinicio y actualización in-place en el HONOR 200.

Hasta completar esa cadena, `com.termux` se considera una excepción de
compatibilidad de PREFIX, no una dependencia de Google Play.

## Gate físico

Antes de retirar `playcompat`:
- instalar la APK direct sobre la NewTermux actual con firma compatible;
- verificar HOME y PREFIX intactos;
- ejecutar login, sh, bash, pkg, apt, proot y CLIs;
- reiniciar el HONOR 200;
- repetir smoke tests;
- confirmar origen de instalación no-Play;
- conservar informe y hashes.

No fusionar esta rama a la cadena principal hasta completar CI y prueba física.
