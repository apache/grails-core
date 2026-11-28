/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.apache.grails.forge.cli.health

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleRunner

/**
 * What one check of {@code project health} found: one finding, or that it found nothing, or that it
 * could not decide. The id is the name of the check and of the finding, as {@code java-target.release},
 * and is never renamed: a tool and the upgrade skill go by it.
 *
 * @since 9.0
 */
@CompileStatic
final class HealthFinding {

    /** Where a finding of the checks of the command line itself comes from: a plugin would name its coordinates. */
    public static final String BUILT_IN = 'built-in'

    /** The name of the finding of a check that found nothing. */
    public static final String OK = 'ok'

    /** The name of the finding of a check that could not decide. */
    public static final String SKIPPED = 'skipped'

    /** {@code check.finding} */
    final String id

    /** The check that found it. */
    final String check

    final HealthLevel level

    /** What was found, as one line, with the file it was found in. */
    final String message

    /** What to do about it, as one line; {@code null} for a check that found nothing. */
    final String fix

    /** The command line that is the fix, or does most of it; {@code null} where there is none. */
    final String command

    /** The files of the project the finding is about, as paths within the project. */
    final List<String> files

    /** The upgrade note that says more, as the address of the guide; {@code null} where none does. */
    final String upgradeNote

    final String origin = BUILT_IN

    HealthFinding(String check, String finding, HealthLevel level, String message, String fix, String command, List<String> files, String upgradeNote) {
        this.id = "${check}.${finding}".toString()
        this.check = check
        this.level = level
        this.message = GradleRunner.line(message)
        this.fix = fix == null ? null : GradleRunner.line(fix)
        this.command = command == null ? null : GradleRunner.line(command)
        this.files = Collections.unmodifiableList(new ArrayList<String>(files ?: Collections.<String>emptyList()))
        this.upgradeNote = upgradeNote
    }

    /**
     * @return the finding as the JSON document has it: every member, always, in this order
     */
    Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>()
        map.put('id', id)
        map.put('check', check)
        map.put('level', level.written)
        map.put('message', message)
        map.put('fix', fix)
        map.put('command', command)
        map.put('files', new ArrayList<String>(files))
        map.put('upgradeNote', upgradeNote)
        map.put('origin', origin)
        return map
    }
}
