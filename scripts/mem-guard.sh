#!/usr/bin/env bash
# Memory guard for a development host that runs an Android emulator next to builds and agents.
# Prints available memory and emulator RSS. Exits 1 if MemAvailable is below the reserve
# (the larger of 4 GiB or 20% of RAM) plus the headroom a planned step needs, or if more
# than one emulator is running.
#
#   scripts/mem-guard.sh                 # check the reserve only
#   scripts/mem-guard.sh --need 3        # also require 3 GiB for a planned step (e.g. a build)
#   scripts/mem-guard.sh --emulator-max 12   # also fail if an emulator exceeds 12 GiB RSS
set -euo pipefail

need_gib=0
emu_max_gib=0
while [ $# -gt 0 ]; do
    case "$1" in
        --need) need_gib="$2"; shift 2 ;;
        --emulator-max) emu_max_gib="$2"; shift 2 ;;
        *) echo "usage: $0 [--need GiB] [--emulator-max GiB]" >&2; exit 2 ;;
    esac
done

kib() { awk -v k="$1" '$1 == k":" { print $2 }' /proc/meminfo; }
total=$(kib MemTotal)
avail=$(kib MemAvailable)
reserve=$(( total / 5 ))
min_reserve=$(( 4 * 1024 * 1024 ))
[ "$reserve" -lt "$min_reserve" ] && reserve=$min_reserve
required=$(( reserve + need_gib * 1024 * 1024 ))

gib() { awk -v k="$1" 'BEGIN { printf "%.1f GiB", k / 1048576 }'; }
echo "MemAvailable $(gib "$avail") of $(gib "$total"); reserve $(gib "$reserve"); required $(gib "$required")"

status=0
emus=0
while read -r pid rss comm args; do
    [ -z "${pid:-}" ] && continue
    case "$comm" in qemu-system*) ;; *) continue ;; esac
    emus=$(( emus + 1 ))
    avd=$(echo "$args" | sed -n 's/.*-avd \([^ ]*\).*/\1/p')
    echo "emulator $avd (pid $pid): $(gib "$rss") RSS"
    if [ "$emu_max_gib" != 0 ] && [ "$rss" -gt $(( emu_max_gib * 1024 * 1024 )) ]; then
        echo "FAIL: $avd exceeds ${emu_max_gib} GiB; cold-restart it between test batches" >&2
        status=1
    fi
done < <(ps -eo pid=,rss=,comm=,args= | grep -- ' -avd ' || true)

if [ "$emus" -gt 1 ]; then
    echo "FAIL: $emus emulators running; only one is allowed on this machine" >&2
    status=1
fi
if [ "$avail" -lt "$required" ]; then
    echo "FAIL: not enough memory; pause memory-heavy work" >&2
    status=1
fi
exit $status
