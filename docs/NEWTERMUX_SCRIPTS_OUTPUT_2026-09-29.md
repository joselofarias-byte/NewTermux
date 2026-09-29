# Scripts, componentes y salidas largas

Esta rama parte de la punta de `agent/newtermux-extra-key-colors-20260928` (PR #27). No modifica `main` ni publica APK.

- **Más → Mis scripts**: crear, editar, ejecutar y eliminar archivos `.sh` en `$HOME/.newtermux/scripts`. Los nombres admiten letras ASCII, números, `-` y `_`. Ejecutar pasa el archivo a `bash` en la sesión actual. Estos archivos quedan dentro de HOME para su respaldo con TBM.
- **Más → Instalar componentes y PRoot**: comandos confirmados para `proot-distro`, Debian, Git/SSH, Python, Node.js, Go y compilación. Se ejecutan en la sesión actual; `proot-distro install debian` requiere instalar antes `proot-distro`.
- **Más → Guardado automático de salidas**: umbral de líneas configurable; `0` desactiva (valor inicial). Al alcanzar el umbral, se crea `Descargas/NewTermux/NewTermux-salida-*.log.gz` con la salida desde el inicio de esa sesión y se siguen anexando miembros gzip completos. Se captura el flujo PTY, incluidos códigos ANSI y potencialmente datos privados; no se puede distinguir la salida de cada comando dentro de un shell. Límite de 32 MiB de datos por sesión, con marcador de truncamiento. Las sesiones anteriores a activar la opción sólo pueden registrarse desde el momento de activación.

El archivo comprimido es un registro del flujo de la terminal, no una transcripción limpia. La exportación manual TXT del historial visible sigue disponible. Validar en HONOR 200 especialmente el permiso y modo de anexado MediaStore, el manejo de sesiones en segundo plano y el script sobre el HOME restaurado.
