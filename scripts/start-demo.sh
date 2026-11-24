#!/usr/bin/env sh
set -eu
echo 'The legacy demo entry now starts platform dev.'
exec sh "$(dirname "$0")/start-dev.sh" "$@"
