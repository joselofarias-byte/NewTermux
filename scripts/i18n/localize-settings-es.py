#!/usr/bin/env python3
"""One-shot, hash-guarded migration of the audited Compose settings UI.

Normal application builds consume the committed Kotlin/Java/XML output, not this
script. Run --check to verify resource parity and prevent the audited English UI
literals from returning. Only explicitly listed files and literals are changed.
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import re
from pathlib import Path
from xml.etree import ElementTree as ET
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[2]
ACT = "app/src/main/java/com/termux/app/activities/"
FEATURE = "app/src/main/java/com/newtermux/features/"
RES = "app/src/main/res/"
BASE = "343069e32d055101d006c501db5f7e4fa6db2a66"
FILES = {
    ACT + "SettingsActivity.kt": "408bfe4b0b3e7c3610e21440fab23893df87ecf7",
    ACT + "FileManagerActivity.kt": "debff6ebe2500192411b081741f169a8b38c1a6f",
    ACT + "SshManagerActivity.kt": "f1090644bfb1acded564917c2810ade231bbb001",
    ACT + "ThemePickerActivity.kt": "84629f42d73f6b506af3112f7918b62b125a66cc",
    FEATURE + "ColorPickerDialog.java": "06582f76a702ab05c9eeaac9b2e806759da494a4",
    RES + "layout/dialog_color_picker_hsv.xml": "555360464dda8591db1a3a5f9dc0dba7264acea6",
}
STRINGS = "newtermux_settings_strings.xml"
PREFIX = "nt_l10n_"
resources: dict[str, tuple[str, str]] = {}
mappings: dict[str, dict[str, str]] = collections.defaultdict(dict)
# This lexer deliberately leaves comments and character literals untouched.
TOKENS = re.compile(r'//[^\n]*|/\*[\s\S]*?\*/|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')


def literal(text: str) -> str:
    return json.dumps(text, ensure_ascii=False)


def resource(key: str, en: str, es: str) -> str:
    key = PREFIX + key
    if key in resources and resources[key] != (en, es):
        raise ValueError("Conflicting resource: " + key)
    resources[key] = (en, es)
    return "R.string." + key


def add(scope: str, key: str, source: str, es: str, *, en: str | None = None,
        args: str = "", receiver: str = "context") -> None:
    ref = resource(key, source if en is None else en, es)
    expr = receiver + ".getString(" + ref + (", " + args if args else "") + ")"
    mappings[scope][literal(source)] = expr


def rows(scope: str, text: str, receiver: str = "context") -> None:
    for row in text.strip().splitlines():
        key, source, es = row.split("|", 2)
        add(scope, key, source, es, receiver=receiver)


COMMON = """
back|Back|Atrás
more|More|Más opciones
save|Save|Guardar
cancel|Cancel|Cancelar
delete|Delete|Eliminar
edit|Edit|Editar
ok|OK|Aceptar
apply|Apply|Aplicar
"""
for scope in ("SettingsActivity.kt", "FileManagerActivity.kt", "SshManagerActivity.kt", "ThemePickerActivity.kt"):
    rows(scope, COMMON)
rows("ColorPickerDialog.java", COMMON, "mContext")

rows("SettingsActivity.kt", """
settings|Settings|Ajustes
appearance|Appearance|Apariencia
appearance_summary|Accent color and UI theme|Color de acento y tema de la interfaz
packages|Package Manager|Gestor de paquetes
packages_summary|Browse, search and install packages|Explora, busca e instala paquetes
ssh|SSH Manager|Gestor SSH
ssh_summary|Save and connect to multiple SSH servers|Guarda conexiones y conéctate a servidores SSH
files|File Manager|Gestor de archivos
files_summary|Browse, edit, and manage files in your Termux home|Explora, edita y organiza los archivos de tu carpeta personal
backup_restore|Backup & Restore|Respaldo y restauración
backup_summary|Back up or restore your Termux environment|Respalda o restaura tu entorno de Termux
features|Features|Funciones
features_summary|Toggle NewTermux features on or off|Activa o desactiva las funciones de NewTermux
expansions|Text Expansion|Atajos de texto
expansions_summary|Auto-expand short triggers to full commands|Convierte abreviaturas en comandos completos
termux_summary|Terminal, keyboard and debugging options|Opciones de terminal, teclado y depuración
api_summary|API plugin settings|Ajustes del complemento API
float_summary|Floating window plugin settings|Ajustes del complemento de ventana flotante
tasker_summary|Tasker plugin settings|Ajustes del complemento Tasker
widget_summary|Widget plugin settings|Ajustes del complemento de accesos directos
about|About|Acerca de NewTermux
about_summary|App, device and plugin info|Información de la aplicación, el dispositivo y los complementos
donate|Donate|Donar
donate_summary|Support development|Apoya el desarrollo
keyboard|Keyboard|Teclado
keyboard_suggestions|Keyboard Suggestions|Sugerencias del teclado
keyboard_suggestions_summary|Show autocorrect and word suggestions bar|Muestra la barra de autocorrección y sugerencias de palabras
autocorrect|Command Autocorrect|Autocorrección de comandos
autocorrect_summary|Suggest corrections for mistyped commands (spacebar)|Sugiere correcciones al pulsar la barra espaciadora
url_detection|URL Detection|Detección de enlaces
url_detection_summary|Long-press a URL in the terminal to open or copy it|Mantén pulsado un enlace en la terminal para abrirlo o copiarlo
extra_keys|Show Extra Keys Toolbar|Mostrar barra de teclas adicionales
extra_keys_summary|Show the ESC, TAB, arrow key row above the keyboard|Muestra ESC, TAB y las flechas sobre el teclado
extra_keys_drawer|Extra Keys in Right Drawer|Teclas adicionales en el panel derecho
extra_keys_drawer_summary|Move extra keys to a swipeable right-side drawer — takes effect on restart|Mueve las teclas adicionales al panel lateral derecho. Se aplica al reiniciar la aplicación.
session_tabs|Session Tabs|Pestañas de sesiones
show_session_tabs|Show Session Tabs|Mostrar pestañas de sesiones
show_session_tabs_summary|Show session tab chips at the top|Muestra las pestañas de las sesiones en la parte superior
session_rename|Session Renaming|Cambiar nombre de las sesiones
session_rename_summary|Long-press a session tab to rename it|Mantén pulsada una pestaña para cambiar su nombre
startup|Startup|Inicio
startup_script|Startup Script|Script de inicio
startup_script_summary|Run ~/.termux/startup-script.sh in each new session|Ejecuta ~/.termux/startup-script.sh en cada sesión nueva
edit_startup|Edit Startup Script|Editar script de inicio
edit_startup_summary|Edit ~/.termux/startup-script.sh|Edita ~/.termux/startup-script.sh
shell|Shell|Intérprete de comandos
zsh_installed|✓ Installed|✓ Instalado
install_zsh|Install Zsh|Instalar Zsh
install_zsh_summary|Required for syntax highlighting and autosuggestions|Necesario para el resaltado de sintaxis y las sugerencias automáticas
drawer|Drawer|Panel lateral
export_screen_script|Export Screen & Make Script|Exportar pantalla y crear script
export_screen_script_summary|Show Export Screen and Make Script buttons in the drawer|Muestra los botones para exportar la pantalla y crear un script en el panel lateral
pkg_update|Pkg Update Button|Botón para actualizar paquetes
pkg_update_summary|Show a button that runs pkg update && pkg upgrade -y|Muestra un botón que ejecuta pkg update && pkg upgrade -y
drawer_commands|Drawer Command Buttons|Botones de comandos del panel lateral
drawer_commands_summary|Show customisable command shortcut buttons|Muestra botones personalizables para ejecutar comandos
background|Background|Segundo plano
keep_alive|Keep alive in background|Mantener activo en segundo plano
keep_alive_summary|Hold a foreground wake lock while sessions run so the app survives when you launch a game. Turn off to save battery.|Mantiene activo el dispositivo mientras hay sesiones abiertas para reducir las interrupciones al usar otras aplicaciones. Desactívalo para ahorrar batería.
permissions|Permissions|Permisos
storage_permission|Grant Storage Permission|Permitir acceso al almacenamiento
storage_permission_summary|Allow access to /sdcard and set up ~/storage symlinks|Permite acceder a /sdcard y crea los enlaces de ~/storage
restart_required|Restart Required|Se necesita una sesión nueva
restart_required_summary|Start a new terminal session for this change to take effect.|Abre una sesión nueva de terminal para aplicar este cambio.
shell_enhancements|Shell Enhancements|Mejoras del intérprete de comandos
shell_enhancements_summary|Autosuggestions + syntax highlighting (requires Zsh)|Sugerencias automáticas y resaltado de sintaxis. Requiere Zsh.
shell_enhancements_unavailable|Install Zsh first to enable this|Instala Zsh para activar esta función
startup_saved|Startup script saved|Script de inicio guardado
enable_expansions|Enable Text Expansion|Activar atajos de texto
add_new|Add New|Agregar atajo
add_new_summary|Create a new expansion|Crea un nuevo atajo de texto
no_expansions|No expansions yet. Tap 'Add New' to create one.|Todavía no hay atajos. Pulsa «Agregar atajo» para crear uno.
edit_expansion|Edit Expansion|Editar atajo
add_expansion|Add Expansion|Agregar atajo
trigger|Trigger (e.g. ;ll)|Abreviatura (por ejemplo, ;ll)
expansion|Expansion (e.g. ls -la)|Comando completo (por ejemplo, ls -la)
trigger_empty|Trigger cannot be empty|La abreviatura no puede estar vacía
backup_home_busy|Backing up home…|Respaldando la carpeta personal…
backup_full_busy|Backing up home + usr…|Respaldando home y usr…
backup_complete|Backup complete|Respaldo completado
backup_home|Basic backup (home only)|Respaldo básico (solo home)
backup_home_summary|Save a .tar.gz of your home directory|Guarda tu carpeta personal (home) en un archivo .tar.gz
backup_full|Full backup (home + usr)|Respaldo completo (home + usr)
backup_full_summary|Save a .tar.gz of home and usr|Guarda las carpetas home y usr en un archivo .tar.gz
restore_from|Restore from backup|Restaurar desde un respaldo
restore_from_summary|Pick a .tar.gz to restore|Selecciona un archivo .tar.gz para restaurar
backup_type|What type of backup is this?|¿Qué tipo de respaldo es?
backup_type_summary|Choose the correct type so the right restore method is used.|Selecciona el tipo correcto para utilizar el método de restauración adecuado.
full|Full (home + usr)|Completo (home + usr)
basic|Basic (home only)|Básico (solo home)
restore_termux|Restore Termux|Restaurar Termux
restore_confirm_full|This will overwrite your home and usr directories. Continue?|Se sobrescribirán tus carpetas home y usr. ¿Deseas continuar?
restore_confirm_home|This will overwrite your home directory. Continue?|Se sobrescribirá tu carpeta personal (home). ¿Deseas continuar?
restoring|Restoring…|Restaurando…
restore_complete|Restore complete|Restauración completada
restore|Restore|Restaurar
debugging|Debugging|Depuración
debugging_summary|Log level and debug options|Nivel de registro y opciones de depuración
terminal_io|Terminal I/O|Entrada y salida de la terminal
terminal_io_summary|Soft keyboard behavior|Comportamiento del teclado en pantalla
terminal_view|Terminal View|Vista de la terminal
terminal_view_summary|Terminal margin adjustment|Ajuste de los márgenes de la terminal
unavailable|Unavailable|No disponible
soft_keyboard|Soft keyboard enabled|Activar teclado en pantalla
soft_keyboard_summary|Show the on-screen keyboard|Muestra el teclado en pantalla
no_hardware_keyboard|Only if no hardware keyboard|Solo cuando no hay un teclado físico
no_hardware_keyboard_summary|Hide soft keyboard when a hardware keyboard is connected|Oculta el teclado en pantalla al conectar un teclado físico
view|View|Vista
terminal_margins|Terminal margin adjustment|Ajustar márgenes de la terminal
terminal_margins_summary|Auto-adjust margins to avoid rounded corners/cutouts|Ajusta los márgenes para evitar las esquinas redondeadas y los recortes de la pantalla
logging|Logging|Registro de actividad
log_level|Log level|Nivel de registro
key_logging|Terminal view key logging|Registrar pulsaciones de teclas
key_logging_summary|Log key events (verbose)|Registra las pulsaciones de teclas con detalle
plugin_errors|Plugin error notifications|Notificaciones de errores de los complementos
crash_reports|Crash report notifications|Notificaciones de fallos de la aplicación
output_unavailable|Could not open output|No se pudo abrir el archivo de destino
input_unavailable|Could not open input|No se pudo abrir el archivo de origen
invalid_path|Invalid path|Ruta no válida
generic_error|error|error
""")
add("SettingsActivity.kt", "save_failed", "Failed to save: ${e.message}", "No se pudo guardar: %1$s", en="Failed to save: %1$s", args="e.message")
add("SettingsActivity.kt", "backup_failed", "Backup failed: $err", "No se pudo completar el respaldo: %1$s", en="Backup failed: %1$s", args="err")
add("SettingsActivity.kt", "restore_failed", "Restore failed: $err", "No se pudo completar la restauración: %1$s", en="Restore failed: %1$s", args="err")
add("SettingsActivity.kt", "tar_exit", "tar exited with code $exit", "tar terminó con el código %1$d", en="tar exited with code %1$d", args="exit")
for key, source, en, es in (
    ("quick_bar", "Barra rápida", "Quick toolbar", "Barra rápida"),
    ("ac_button", "Botón AC", "AC button", "Botón AC"),
    ("ac_button_summary", "Mostrar el interruptor de autocorrección en la barra", "Show the autocorrect switch in the toolbar", "Mostrar el interruptor de autocorrección en la barra"),
    ("microphone", "Micrófono", "Microphone", "Micrófono"),
    ("microphone_summary", "Mostrar dictado por voz en la barra", "Show voice input in the toolbar", "Mostrar dictado por voz en la barra"),
    ("quick_packages", "Paquetes", "Packages", "Paquetes"),
    ("quick_packages_summary", "Mostrar el acceso rápido al gestor de paquetes", "Show the package manager shortcut", "Mostrar el acceso rápido al gestor de paquetes"),
    ("clear", "Limpiar", "Clear", "Limpiar"),
    ("clear_summary", "Mostrar el botón para limpiar la terminal", "Show the clear terminal button", "Mostrar el botón para limpiar la terminal"),
    ("quick_more", "Más (⋮)", "More (⋮)", "Más (⋮)"),
    ("quick_more_summary", "Siempre visible: TXT, pegar, inicio, final, teclado, archivos y autocorrección", "Always visible: TXT, paste, HOME, END, keyboard, files and autocorrect", "Siempre visible: TXT, pegar, HOME, END, teclado, archivos y autocorrección"),
):
    add("SettingsActivity.kt", key, source, es, en=en)

rows("FileManagerActivity.kt", """
files|File Manager|Gestor de archivos
up|Up|Subir o volver
new_file|New File|Nuevo archivo
new_folder|New Folder|Nueva carpeta
empty_folder|Empty folder|Carpeta vacía
file_too_large|File too large to edit in-app|El archivo es demasiado grande para editarlo en la aplicación
saved|Saved|Guardado
share|Share|Compartir
copy_path|Copy path|Copiar ruta
deleted|Deleted|Eliminado
delete_failed|Delete failed|No se pudo eliminar
filename_hint|filename.txt|archivo.txt
foldername_hint|folder-name|nombre-de-carpeta
file_exists|File already exists|El archivo ya existe
folder_failed|Could not create folder|No se pudo crear la carpeta
folder|Folder|Carpeta
create|Create|Crear
share_unavailable|Share unavailable, path copied|No se pudo compartir. Se copió la ruta.
path_copied|Path copied|Ruta copiada
""")
add("FileManagerActivity.kt", "delete_named", 'Delete "${file.name}"?', '¿Eliminar «%1$s»?', en='Delete "%1$s"?', args="file.name")
add("FileManagerActivity.kt", "error_detail", "Error: ${e.message}", "Error: %1$s", en="Error: %1$s", args="e.message")
add("FileManagerActivity.kt", "editor_save_failed", "Save failed: ${e.message}", "No se pudo guardar: %1$s", en="Save failed: %1$s", args="e.message")
add("FileManagerActivity.kt", "share_named", "Share ${file.name}", "Compartir %1$s", en="Share %1$s", args="file.name")

rows("SshManagerActivity.kt", """
ssh|SSH Manager|Gestor SSH
add_profile|Add profile|Agregar perfil
connect|Connect|Conectar
delete_profile|Delete Profile|Eliminar perfil
edit_profile|Edit Profile|Editar perfil
add_ssh_profile|Add SSH Profile|Agregar perfil SSH
nickname|Nickname|Nombre del perfil
host|Host|Servidor
port|Port|Puerto
username|Username|Usuario
private_key|Private key path (blank = password)|Ruta de la clave privada (vacía para usar contraseña)
port_forwarding|Port forwarding|Redirección de puertos
local_tunnel|Local (-L)|Local (-L)
remote_tunnel|Remote (-R)|Remota (-R)
local_port|Local port|Puerto local
remote_host|Remote host|Servidor remoto
remote_port|Remote port|Puerto remoto
ssh_required|Nickname, host, and username are required|Debes indicar el nombre del perfil, el servidor y el usuario
""")
add("SshManagerActivity.kt", "no_ssh_profiles", "No SSH profiles yet.\nTap + to add one.", "Todavía no hay perfiles SSH.\nPulsa + para agregar uno.")
add("SshManagerActivity.kt", "connect_named", "Connect to ${profile.nickname}", "Conectar a %1$s", en="Connect to %1$s", args="profile.nickname")
add("SshManagerActivity.kt", "delete_named", 'Delete "${profile.nickname}"?', '¿Eliminar «%1$s»?', en='Delete "%1$s"?', args="profile.nickname")
add("SshManagerActivity.kt", "tunnel_line", "\nTunnel: $tunnel", "\nTúnel: %1$s", en="\nTunnel: %1$s", args="tunnel")
add("SshManagerActivity.kt", "tunnel", "Tunnel: $it", "Túnel: %1$s", en="Tunnel: %1$s", args="it")

rows("ThemePickerActivity.kt", """
themes_colors|Themes & Colors|Temas y colores
custom_theme|Custom Theme|Tema personalizado
terminal_theme|Terminal Theme|Tema de la terminal
accent_color|Accent Color|Color de acento
custom_color_slot|Custom…|Personalizado…
custom_scope|Custom Theme Scope|Colores del tema personalizado
core_colors|Core 3  (Background, Foreground, Cursor)|Básicos: fondo, texto y cursor
""")
add("ThemePickerActivity.kt", "all_colors", "All 18 terminal colors", "Todos los colores de la terminal (19)", en="All 19 terminal colors")
rows("ColorPickerDialog.java", """
color_wheel|Color Wheel (HSV)|Rueda de color (HSV)
rgb_sliders|RGB Sliders|Controles deslizantes RGB
picker_style|Choose Picker Style|Elige el selector de color
next|Next|Siguiente
custom_hsv|Custom Color (HSV)|Color personalizado (HSV)
custom_rgb|Custom Color (RGB)|Color personalizado (RGB)
""", "mContext")

# Resource-only labels: no persisted theme key or color value is translated.
COLOR_LABELS = [("color_background", "Background", "Fondo"),
                ("color_foreground", "Foreground", "Texto"),
                ("color_cursor", "Cursor", "Cursor")]
for index, (en, es) in enumerate(zip(
        ("Black", "Red", "Green", "Yellow", "Blue", "Magenta", "Cyan", "White",
         "Bright Black", "Bright Red", "Bright Green", "Bright Yellow", "Bright Blue", "Bright Magenta", "Bright Cyan", "Bright White"),
        ("Negro", "Rojo", "Verde", "Amarillo", "Azul", "Magenta", "Cian", "Blanco",
         "Negro brillante", "Rojo brillante", "Verde brillante", "Amarillo brillante", "Azul brillante", "Magenta brillante", "Cian brillante", "Blanco brillante"))):
    COLOR_LABELS.append((f"color_{index}", f"Color {index} ({en})", f"Color {index} ({es})"))
for key, en, es in COLOR_LABELS:
    resource(key, en, es)
ACCENTS = [("purple", "Purple", "Violeta"), ("blue", "Blue", "Azul"),
           ("green", "Green", "Verde"), ("orange", "Orange", "Naranja"),
           ("red", "Red", "Rojo"), ("teal", "Teal", "Turquesa"),
           ("pink", "Pink", "Rosa"), ("gold", "Gold", "Dorado"),
           ("white", "White", "Blanco")]
for key, en, es in ACCENTS:
    resource("accent_" + key, en, es)
THEME_LABELS = [("default_dark", "Default Dark", "Oscuro predeterminado"),
                ("oled_black", "OLED Black", "Negro OLED"),
                ("amber", "Amber", "Ámbar"),
                ("low_contrast", "Low Contrast", "Contraste suave"),
                ("custom", "Custom", "Personalizado")]
for key, en, es in THEME_LABELS:
    resource("theme_" + key, en, es)
resource("brightness", "Brightness", "Brillo")
for key, en, es in [("log_off", "Off (%1$d)", "Desactivado (%1$d)"),
                    ("log_normal", "Normal (%1$d)", "Normal (%1$d)"),
                    ("log_debug", "Debug (%1$d)", "Depuración (%1$d)"),
                    ("log_verbose", "Verbose (%1$d)", "Detallado (%1$d)")]:
    resource(key, en, es)


def exact(text: str, before: str, after: str, count: int = 1) -> str:
    found = text.count(before)
    if found != count:
        raise ValueError(f"Expected {count} occurrences, found {found}: {before[:100]!r}")
    return text.replace(before, after)


def inject_context(text: str, name: str) -> str:
    start = text.index("private fun " + name + "(")
    pos = text.index(") {", start) + 3
    return text[:pos] + "\n    val context = LocalContext.current" + text[pos:]


def transform(path: str, text: str) -> str:
    name = Path(path).name
    if name.endswith(".kt"):
        text = exact(text, "package com.termux.app.activities\n", "package com.termux.app.activities\n\nimport com.termux.R\n")
    if name == "SettingsActivity.kt":
        for function in ("SettingsScaffold", "TermuxScreen"):
            text = inject_context(text, function)
        text = exact(text,
            "    val labels = remember { Logger.getLogLevelLabelsArray(context, Logger.getLogLevelsArray(), true).map { it.toString() } }",
            """    val labels = Logger.getLogLevelsArray().map { level ->
        val label = when (level) {
            Logger.LOG_LEVEL_OFF -> R.string.nt_l10n_log_off
            Logger.LOG_LEVEL_NORMAL -> R.string.nt_l10n_log_normal
            Logger.LOG_LEVEL_DEBUG -> R.string.nt_l10n_log_debug
            Logger.LOG_LEVEL_VERBOSE -> R.string.nt_l10n_log_verbose
            else -> null
        }
        if (label == null) level.toString() else context.getString(label, level)
    }""")
    elif name == "FileManagerActivity.kt":
        for function in ("FileRow", "TextEditorDialog", "NameEntryDialog"):
            text = inject_context(text, function)
        # Keep action dispatch independent of the displayed/localized label.
        for source, key in (("Edit", "edit"), ("Share", "share"), ("Copy path", "copy_path"), ("Delete", "delete")):
            text = exact(text, "add(" + literal(source) + ")", "add(R.string." + PREFIX + key + ")")
            text = exact(text, literal(source) + " ->", "R.string." + PREFIX + key + " ->")
        text = exact(text, "text = { Text(action) }", "text = { Text(context.getString(action)) }")
    elif name == "SshManagerActivity.kt":
        text = exact(text, "import androidx.compose.ui.Modifier\n", "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.platform.LocalContext\n")
        for function in ("SshManagerScreen", "ProfileCard", "SshEditorDialog"):
            text = inject_context(text, function)
    elif name == "ThemePickerActivity.kt":
        text = inject_context(text, "TerminalThemeCard")
        text = exact(text, "List<Pair<String, String>>", "List<Pair<String, Int>>", 2)
        text = exact(text, "Text(label, modifier = Modifier.weight(1f)", "Text(context.getString(label), modifier = Modifier.weight(1f)")
        for key, en, es in COLOR_LABELS:
            before = " to " + literal(en)
            expected = 2 if key in ("color_background", "color_foreground", "color_cursor") else 1
            text = exact(text, before, " to R.string." + PREFIX + key, expected)
        text = exact(text, "NewTermuxTheme.getColorName(color)", "accentColorName(context, color)")
        text = exact(text, "NewTermuxColorTheme.getThemeName(key)", "terminalThemeName(context, key)")
        text += "\n\n// Display labels are localized; persisted keys and actual colors stay unchanged.\n"
        text += "private fun accentColorName(context: android.content.Context, color: Int): String {\n"
        text += "    val labels = intArrayOf(" + ", ".join("R.string." + PREFIX + "accent_" + key for key, _, _ in ACCENTS) + ")\n"
        text += "    val index = NewTermuxTheme.COLORS.indexOf(color)\n"
        text += "    return if (index in labels.indices) context.getString(labels[index]) else NewTermuxTheme.getColorName(color)\n}\n"
        text += "\nprivate fun terminalThemeName(context: android.content.Context, key: String): String = when (key) {\n"
        for key, _, _ in THEME_LABELS:
            text += "    " + literal(key) + " -> context.getString(R.string." + PREFIX + "theme_" + key + ")\n"
        text += "    else -> NewTermuxColorTheme.getThemeName(key)\n}\n"
    elif name == "dialog_color_picker_hsv.xml":
        return exact(text, 'android:text="Brightness"', 'android:text="@string/nt_l10n_brightness"')

    mapping = mappings[name]
    found = collections.Counter()
    def replace(match: re.Match[str]) -> str:
        token = match.group(0)
        if token in mapping:
            found[token] += 1
            return mapping[token]
        return token
    result = TOKENS.sub(replace, text)
    # Special structural rewrites above legitimately consume some source labels.
    unused = set(mapping) - set(found)
    optional = {literal(row.split("|", 2)[1]) for row in COMMON.strip().splitlines()}
    if name == "FileManagerActivity.kt":
        optional |= {literal("Share"), literal("Copy path")}
    if unused - optional:
        raise ValueError(f"Unaudited/missing source literals in {name}: {unused - optional}")
    return result


def xml_text(text: str) -> str:
    text = text.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n")
    return escape(text)


def make_xml(language: int) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for key, pair in sorted(resources.items()):
        lines.append('    <string name="' + key + '">' + xml_text(pair[language]) + '</string>')
    return "\n".join(lines + ["</resources>", ""])


def check() -> None:
    trees = []
    for folder in ("values", "values-es"):
        root = ET.parse(ROOT / RES / folder / STRINGS).getroot()
        entries = {node.attrib["name"]: node.text or "" for node in root}
        if len(entries) != len(list(root)):
            raise AssertionError("Duplicate resource in " + folder)
        if set(entries) != set(resources):
            raise AssertionError("Missing or unexpected translations in " + folder)
        trees.append(entries)
    fmt = re.compile(r"%\d+\$[sd]")
    for key in resources:
        assert fmt.findall(trees[0][key]) == fmt.findall(trees[1][key]), key
    total_references = 0
    for path in FILES:
        text = (ROOT / path).read_text(encoding="utf-8")
        scope = Path(path).name
        for match in TOKENS.finditer(text):
            assert match.group(0) not in mappings.get(scope, {}), (path, match.group(0))
        refs = re.findall(r"(?:R\.string\.|@string/)(nt_l10n_[a-z0-9_]+)", text)
        assert set(refs) <= set(resources), (path, set(refs) - set(resources))
        total_references += len(refs)
    settings = (ROOT / ACT / "SettingsActivity.kt").read_text(encoding="utf-8")
    for command in ('"pkg install zsh\\n"', '"-zcf"', '"-zxvf"', '"--recursive-unlink"', '"--preserve-permissions"', '".termux/startup-script.sh"'):
        assert command in settings, command
    ssh = (ROOT / ACT / "SshManagerActivity.kt").read_text(encoding="utf-8")
    assert 'profile.tunnelType = if (tunnelRemote) "remote" else "local"' in ssh
    assert 'profile.buildCommand() + "\\n"' in ssh
    theme = (ROOT / ACT / "ThemePickerActivity.kt").read_text(encoding="utf-8")
    assert '"background" to R.string.nt_l10n_color_background' in theme
    assert '"color15" to R.string.nt_l10n_color_15' in theme
    assert '"ls -la"' in theme and '"home"' in theme
    files = (ROOT / ACT / "FileManagerActivity.kt").read_text(encoding="utf-8")
    for key in ("edit", "share", "copy_path", "delete"):
        assert "R.string." + PREFIX + key + " ->" in files
    print(f"PASS: {len(resources)} English/Spanish resource pairs; {total_references} resolved references")
    print("PASS: audited UI literals removed; formatting arguments match; commands, SSH keys and theme keys preserved")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.check:
        check()
        return
    outputs = {}
    # Validate every input before writing anything, to prevent partial migrations.
    for path, expected in FILES.items():
        data = (ROOT / path).read_bytes()
        actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        if actual != expected:
            raise SystemExit(f"STOP: {path} moved from the audited source ({actual} != {expected}). Re-audit; do not force.")
        outputs[path] = transform(path, data.decode("utf-8"))
    for index, folder in enumerate(("values", "values-es")):
        path = RES + folder + "/" + STRINGS
        if (ROOT / path).exists():
            raise SystemExit("STOP: resource file already exists; use --check instead")
        outputs[path] = make_xml(index)
    for path, text in outputs.items():
        destination = ROOT / path
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_text(text, encoding="utf-8")
    check()
    print("Changed files:\n" + "\n".join(sorted(outputs)))
    print("Base source: " + BASE)


if __name__ == "__main__":
    main()
