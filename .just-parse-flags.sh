#!/usr/bin/env bash
# Shared flag parser for the justfile's build/fetch/build-fetch/deploy recipes.
# Usage: .just-parse-flags.sh <default-host> <default-abi> -- [flags...]
# Prints "variant flavor host abi" on stdout.
set -euo pipefail

host="$1"
abi="$2"
shift 2
[ "${1:-}" = "--" ] && shift

variant=phone
flavor=debug

for flag in "$@"; do
    case "$flag" in
        --phone) variant=phone ;;
        --tv) variant=tv ;;
        --debug) flavor=debug ;;
        --release) flavor=release ;;
        --host=*) host="${flag#--host=}" ;;
        --abi=*) abi="${flag#--abi=}" ;;
        *)
            echo "unknown flag: $flag (expected --phone|--tv, --debug|--release, --host=<host>, --abi=<abi>)" >&2
            exit 1
            ;;
    esac
done

echo "$variant $flavor $host $abi"
