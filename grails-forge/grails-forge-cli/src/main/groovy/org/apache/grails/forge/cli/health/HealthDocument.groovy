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

import java.nio.file.Path
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import org.grails.forge.util.VersionInfo

/**
 * The JSON document {@code project health --json} writes, as the other documents of the command
 * line are written: every member is always there, in the same order, and {@code null} where it does
 * not apply; the {@code format} changes only when a reader of the earlier format would misread the
 * document, and a member that is added later comes after the others.
 *
 * @since 9.0
 */
@CompileStatic
final class HealthDocument {

    /** The version of the document. */
    static final int FORMAT = 1

    /** The checks ran; whether the command passed is {@code passed}. */
    static final String CHECKED = 'checked'

    /** The directory is in no project the command can check: see the reason. */
    static final String REFUSED = 'refused'

    /** The command failed while it worked. */
    static final String FAILED = 'failed'

    static final String NO_PROJECT = 'no-project'
    static final String UNTRUSTED_PROJECT = 'untrusted-project'
    static final String ERROR = 'error'

    private final Map<String, Object> members = new LinkedHashMap<>()

    private HealthDocument(Path project, boolean strict) {
        members.put('format', FORMAT)
        members.put('command', 'project health')
        members.put('projectDirectory', project == null ? null : project.toString())
        members.put('outcome', null)
        members.put('reason', null)
        members.put('message', null)
        members.put('projectGrailsVersion', null)
        members.put('commandLineVersion', VersionInfo.getGrailsVersion())
        members.put('online', false)
        members.put('strict', strict)
        members.put('passed', false)
        members.put('counts', null)
        members.put('findings', null)
    }

    /**
     * @param untrusted why the project above the directory is not used, or {@code null} when there is none
     * @param message the line the command says without {@code --json}
     */
    static HealthDocument refused(String untrusted, String message, boolean strict) {
        HealthDocument document = new HealthDocument(null, strict)
        document.members.put('outcome', REFUSED)
        document.members.put('reason', untrusted == null ? NO_PROJECT : UNTRUSTED_PROJECT)
        document.members.put('message', message)
        return document
    }

    static HealthDocument failed(Path project, String message, boolean strict) {
        HealthDocument document = new HealthDocument(project, strict)
        document.members.put('outcome', FAILED)
        document.members.put('reason', ERROR)
        document.members.put('message', message)
        return document
    }

    static HealthDocument checked(Path project, String projectVersion, ProjectHealth health, boolean strict) {
        HealthDocument document = new HealthDocument(project, strict)
        document.members.put('outcome', CHECKED)
        document.members.put('projectGrailsVersion', projectVersion)
        document.members.put('passed', health.failing(strict).isEmpty())
        Map<String, Integer> counts = new LinkedHashMap<>()
        for (HealthLevel level : [HealthLevel.ERROR, HealthLevel.WARNING, HealthLevel.ADVICE, HealthLevel.OK, HealthLevel.SKIPPED]) {
            counts.put(level.written, health.count(level))
        }
        document.members.put('counts', counts)
        document.members.put('findings', health.findings.collect { HealthFinding each -> each.toMap() })
        return document
    }

    /**
     * @return the document, on one line; JSON writes a control character as an escape, so nothing of
     * it acts on a terminal
     */
    String toJson() {
        return JsonOutput.toJson(members)
    }
}
