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
import org.apache.grails.forge.cli.console.ProjectEvidence

/**
 * {@code deprecated-dependency}: the modules of Grails the build file declares that are deprecated
 * for removal, the GPars and the RxJava 1 and 2 modules of the promises and the events, the RxJava 1
 * and 2 libraries themselves, and a GPars module beside the Groovy module that takes its place, of
 * which only one is used. The build file is read as {@link ProjectEvidence} reads it.
 *
 * @since 9.0
 */
@CompileStatic
final class DeprecatedDependencyCheck extends HealthCheck {

    // each GPars module, with the one of Groovy concurrency that takes its place
    private static final Map<String, String> GPARS = ['grails-async-gpars': 'grails-async-groovy-concurrent',
                                                      'grails-events-gpars': 'grails-events-groovy-concurrent'].asImmutable()
    // each module of RxJava 1 or 2, with the one of RxJava 3 that takes its place
    private static final Map<String, String> RXJAVA = ['grails-async-rxjava': 'grails-async-rxjava3', 'grails-async-rxjava2': 'grails-async-rxjava3',
                                                       'grails-events-rxjava': 'grails-events-rxjava3', 'grails-events-rxjava2': 'grails-events-rxjava3'].asImmutable()
    private static final Map<String, String> RXJAVA_LIBRARIES = ['io.reactivex:rxjava': 'io.reactivex.rxjava3:rxjava', 'io.reactivex.rxjava2:rxjava': 'io.reactivex.rxjava3:rxjava'].asImmutable()

    @Override
    String getName() {
        return 'deprecated-dependency'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        ProjectEvidence evidence = context.evidence
        String file = evidence.buildFile
        if (!evidence.buildRead) {
            return [skipped("The project has no ${file} that could be read, so its dependencies are not known", [file])]
        }
        Map<String, String> ofGrails = [:]
        for (String each : evidence.dependencies) {
            int colon = each.indexOf(':')
            String group = each.substring(0, colon)
            if (group.startsWith('org.apache.grails') || group.startsWith('org.grails')) {
                ofGrails.put(each.substring(colon + 1), each)
            }
        }
        List<HealthFinding> findings = []
        List<String> both = GPARS.findAll { String gpars, String groovy -> ofGrails.containsKey(gpars) && ofGrails.containsKey(groovy) }.collect { String gpars, String groovy ->
            "${ofGrails.get(gpars)} and ${ofGrails.get(groovy)}".toString()
        }
        if (!both.isEmpty()) {
            findings << found('gpars-and-groovy', HealthLevel.ERROR, "${file} declares ${both.join(', and ')}: only one promise factory is used, and the " +
                    'event bus does not start when it finds two', 'Remove the GPars module, and keep the one of Groovy concurrency', null, [file], NOTE_GPARS)
        }
        List<String> gpars = GPARS.keySet().findAll { String each -> ofGrails.containsKey(each) && !ofGrails.containsKey(GPARS.get(each)) }.toList()
        if (!gpars.isEmpty()) {
            findings << found('gpars', HealthLevel.WARNING, "${file} declares ${gpars.collect { String each -> ofGrails.get(each) }.join(', ')}, which " +
                    "${gpars.size() == 1 ? 'is' : 'are'} deprecated for removal", "Declare ${gpars.collect { String each -> GPARS.get(each) }.join(' and ')} " +
                    "in ${gpars.size() == 1 ? 'its' : 'their'} place, of the same group", null, [file], NOTE_GPARS)
        }
        List<String> rx = RXJAVA.keySet().findAll { String each -> ofGrails.containsKey(each) }.collect { String each -> ofGrails.get(each) } +
                RXJAVA_LIBRARIES.keySet().findAll { String each -> each in evidence.dependencies }.toList()
        if (!rx.isEmpty()) {
            List<String> replacements = []
            replacements.addAll(RXJAVA.findAll { String old, String now -> ofGrails.containsKey(old) }.values())
            replacements.addAll(RXJAVA_LIBRARIES.findAll { String old, String now -> old in evidence.dependencies }.values())
            replacements = replacements.unique()
            findings << found('rxjava', HealthLevel.WARNING, "${file} declares ${rx.join(', ')}, of RxJava 1 or 2, which ${rx.size() == 1 ? 'is' : 'are'} " +
                    'deprecated for removal', "Declare ${replacements.join(' and ')} in ${rx.size() == 1 ? 'its' : 'their'} place, and port the code to RxJava 3",
                    null, [file], NOTE_RXJAVA)
        }
        if (!findings.isEmpty()) {
            return findings
        }
        if (!evidence.notRead.isEmpty()) {
            return [skipped("${file} declares none of them where it was read, and ${evidence.notRead.first()}", [file])]
        }
        return [ok("${file} declares no module of GPars, RxJava 1 or RxJava 2", [file])]
    }
}
