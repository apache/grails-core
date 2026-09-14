#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# Reports what the machine is doing when a build has stopped saying anything.
#
# GRAILS-16218: the whole repository build has twice stopped producing output after its last
# task and held the runner until the six hour ceiling. Nothing is running at that point that a
# later workflow step could ask, because a later step never arrives - the build never ends. So
# this is started in the background before the build, sleeps past the point where the build
# should have finished, and then says what every JVM is doing.
#
# Two dumps, a minute apart: one says what a thread is doing, two say whether it is doing
# anything at all.
#
# Usage: hang-watchdog.sh [seconds-before-first-dump] [seconds-between-dumps]

set -uo pipefail

FIRST_DUMP_AFTER="${1:-4500}"   # 75 minutes; the job it watches normally finishes in 40-65
SECONDS_BETWEEN="${2:-60}"

dump() {
    echo ""
    echo "########## hang watchdog: ${1} dump at $(date -u +%Y-%m-%dT%H:%M:%SZ)"

    echo "----- processes by start time"
    ps -eo pid,ppid,etime,stat,rss,comm --sort=start_time 2>/dev/null | tail -40

    local pids
    pids="$(jps -q 2>/dev/null)"
    if [ -z "${pids}" ]; then
        echo "----- no JVMs reported by jps"
    fi
    for pid in ${pids}; do
        echo "----- ${pid}: command line"
        jcmd "${pid}" VM.command_line 2>&1 | head -8
        echo "----- ${pid}: heap"
        jcmd "${pid}" GC.heap_info 2>&1 | head -8
        echo "----- ${pid}: threads, with locks"
        jcmd "${pid}" Thread.print -l 2>&1
    done

    echo "----- gradle daemon logs, last lines"
    # shellcheck disable=SC2012
    for log in "${HOME}"/.gradle/daemon/*/daemon-*.out.log; do
        [ -f "${log}" ] || continue
        echo "--- ${log}"
        tail -40 "${log}" 2>/dev/null
    done
}

echo "hang watchdog armed: first dump in ${FIRST_DUMP_AFTER}s, second ${SECONDS_BETWEEN}s later"
sleep "${FIRST_DUMP_AFTER}"
dump "first"
sleep "${SECONDS_BETWEEN}"
dump "second"
echo "hang watchdog done"
