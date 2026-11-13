#!/data/data/com.termux/files/usr/bin/sh
set -eu

backup_dir=${1:-}
case "$backup_dir" in
    "$HOME/.termux/outlands-backups/"*) ;;
    *) printf 'Supply an Outlands theme backup directory in your Termux HOME.\n' >&2; exit 2 ;;
esac

if [ -f "$backup_dir/colors.properties" ]; then
    cp "$backup_dir/colors.properties" "$HOME/.termux/colors.properties"
elif [ -f "$backup_dir/no-previous-color-file" ]; then
    rm -f "$HOME/.termux/colors.properties"
else
    printf 'No theme backup found.\n' >&2
    exit 2
fi
if command -v termux-reload-settings >/dev/null 2>&1; then
    termux-reload-settings
fi
printf 'Previous terminal colors restored.\n'
