#!/data/data/com.termux/files/usr/bin/bash
set -eu

ACTION="${1:-status}"
NT_DIR="$HOME/.newtermux"
MARKER="$NT_DIR/prompt-clock.enabled"
ZSH_SNIPPET="$NT_DIR/prompt-clock.zsh"
BASH_SNIPPET="$NT_DIR/prompt-clock.bash"
START="# >>> NEWTERMUX PROMPT CLOCK >>>"
END="# <<< NEWTERMUX PROMPT CLOCK <<<"

mkdir -p "$NT_DIR"

remove_block() {
  local file="$1"
  [ -f "$file" ] || return 0
  local tmp="${file}.nt-prompt.$$"
  awk -v start="$START" -v end="$END" '
    $0 == start {skip=1; next}
    $0 == end {skip=0; next}
    !skip {print}
  ' "$file" > "$tmp"
  mv -f "$tmp" "$file"
}

append_block() {
  local file="$1" snippet="$2"
  touch "$file"
  remove_block "$file"
  {
    printf '\n%s\n' "$START"
    printf '[ -f "%s" ] && . "%s"\n' "$snippet" "$snippet"
    printf '%s\n' "$END"
  } >> "$file"
}

enable_clock() {
  cat > "$ZSH_SNIPPET" <<'EOF_ZSH'
# NewTermux: timestamp of the moment this prompt was drawn.
# Preserve the user's existing prompt/theme and prepend only the clock.
if [[ "$PROMPT" != *'%D{%H:%M}'* ]]; then
  # Use the terminal's default foreground instead of ANSI color 8.
  # Color 8 is intentionally dark gray and becomes almost invisible on
  # NewTermux's dark themes. Bold default foreground remains readable and
  # automatically follows light/dark terminal themes.
  PROMPT='%B[%D{%H:%M}]%b '"$PROMPT"
fi
EOF_ZSH

  cat > "$BASH_SNIPPET" <<'EOF_BASH'
# NewTermux: timestamp of the moment this prompt was drawn.
case "$PS1" in
  *'\\A'*) ;;
  *) PS1='[\\A] '"$PS1" ;;
esac
EOF_BASH

  append_block "$HOME/.zshrc" "$ZSH_SNIPPET"
  append_block "$HOME/.bashrc" "$BASH_SNIPPET"
  : > "$MARKER"
  echo "Hora en prompt: ACTIVADA"
  echo "Formato: [HH:MM] + prompt actual"
}

disable_clock() {
  remove_block "$HOME/.zshrc"
  remove_block "$HOME/.bashrc"
  rm -f "$MARKER" "$ZSH_SNIPPET" "$BASH_SNIPPET"
  echo "Hora en prompt: DESACTIVADA"
}

case "$ACTION" in
  enable) enable_clock ;;
  disable) disable_clock ;;
  status)
    if [ -f "$MARKER" ]; then
      echo "Hora en prompt: ACTIVADA"
    else
      echo "Hora en prompt: DESACTIVADA"
    fi
    ;;
  *)
    echo "Uso: $0 {enable|disable|status}" >&2
    exit 2
    ;;
esac
