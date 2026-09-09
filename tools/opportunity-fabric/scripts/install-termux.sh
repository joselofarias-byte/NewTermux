#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
pkg update -y
pkg install -y python git
python -m pip install --upgrade pip setuptools
python -m pip install -e .
echo
echo 'Instalado. Pruebas:'
echo '  of inventory'
echo '  of evaluate quantus'
echo '  of policies'
