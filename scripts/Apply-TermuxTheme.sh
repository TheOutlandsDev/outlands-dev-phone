#!/data/data/com.termux/files/usr/bin/sh
set -eu

# Run inside Termux. No root or external-command permission is required.
theme_source=${1:-}
if [ -z "$theme_source" ] || [ ! -f "$theme_source" ]; then
    printf 'Usage: sh Apply-TermuxTheme.sh /path/to/colors.properties\n' >&2
    exit 2
fi

umask 077
theme_dir="$HOME/.termux"
mkdir -p "$theme_dir/outlands-backups"
backup_dir=$(mktemp -d "$theme_dir/outlands-backups/theme-$(date -u +%Y%m%dT%H%M%SZ)-XXXXXXXX")
if [ -f "$theme_dir/colors.properties" ]; then
    cp "$theme_dir/colors.properties" "$backup_dir/colors.properties"
else
    : > "$backup_dir/no-previous-color-file"
fi
cp "$theme_source" "$theme_dir/colors.properties"
if command -v termux-reload-settings >/dev/null 2>&1; then
    termux-reload-settings
fi
printf 'Outlands terminal colors applied. Backup: %s\n' "$backup_dir"
