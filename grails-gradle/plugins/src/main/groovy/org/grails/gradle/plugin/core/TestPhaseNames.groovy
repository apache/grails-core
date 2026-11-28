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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

/**
 * The names of the test phases of a project: every {@code Test} task of the project is one phase,
 * and the name of the phase is what {@code grails test-app} selects it with, as a switch.
 *
 * <p>The task {@code test} is the phase {@code unit} and the task {@code integrationTest} the phase
 * {@code integration}. Any other task whose name ends in {@code Test} is the phase of the name
 * without that ending, and a task of another name is the phase of its own name; the name is
 * written in lower case with hyphens, so that {@code functionalTest} is {@code functional},
 * {@code endToEndTest} is {@code end-to-end} and a test suite {@code smoke} is {@code smoke}.
 * Where two tasks would be one phase, the task {@code test} or {@code integrationTest} is, before a
 * task whose name ends in {@code Test}, before any other: the task that is not the phase has none,
 * and is run by its own name.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class TestPhaseNames {

    /** The phase of the unit tests, the task {@code test}. */
    static final String UNIT = 'unit'

    /** The phase of the integration tests, the task {@code integrationTest}. */
    static final String INTEGRATION = 'integration'

    /** The task of the unit tests. */
    static final String UNIT_TASK = 'test'

    /** The task of the integration tests. */
    static final String INTEGRATION_TASK = 'integrationTest'

    private static final String ENDING = 'Test'

    private TestPhaseNames() {
    }

    /**
     * @param testTasks the names of the {@code Test} tasks of a project
     * @return the task of each phase by the name of the phase: the unit tests first, then the
     * integration tests, then the others by name
     */
    static Map<String, String> phases(Collection<String> testTasks) {
        Map<String, String> phases = new LinkedHashMap<String, String>()
        if (testTasks.contains(UNIT_TASK)) {
            phases.put(UNIT, UNIT_TASK)
        }
        if (testTasks.contains(INTEGRATION_TASK)) {
            phases.put(INTEGRATION, INTEGRATION_TASK)
        }
        List<String> others = new ArrayList<String>(testTasks).sort()
        Map<String, String> named = new TreeMap<String, String>()
        for (String task : others) {
            if (task != UNIT_TASK && task != INTEGRATION_TASK && task.endsWith(ENDING) && task.length() > ENDING.length()) {
                String phase = hyphenated(task.substring(0, task.length() - ENDING.length()))
                if (!phases.containsKey(phase)) {
                    named.putIfAbsent(phase, task)
                }
            }
        }
        for (String task : others) {
            if (task != UNIT_TASK && task != INTEGRATION_TASK && !named.containsValue(task)) {
                String phase = hyphenated(task)
                if (!phases.containsKey(phase)) {
                    named.putIfAbsent(phase, task)
                }
            }
        }
        phases.putAll(named)
        return phases
    }

    /**
     * @param phases the task of each phase of a project, see {@link #phases}
     * @param asked what was given for a phase: its name, also as {@code endToEnd} for {@code end-to-end}
     * @return the name of the phase, or {@code null} when the project has no such phase
     */
    static String find(Map<String, String> phases, String asked) {
        String name = hyphenated(asked)
        return phases.containsKey(name) ? name : null
    }

    /**
     * @param name a name in camel case, as that of a task
     * @return the name in lower case, its words joined by hyphens
     */
    static String hyphenated(String name) {
        StringBuilder hyphenated = new StringBuilder()
        for (int i = 0; i < name.length(); i++) {
            char each = name.charAt(i)
            if (Character.isUpperCase(each)) {
                if (i > 0 && name.charAt(i - 1) != ('-' as char)) {
                    hyphenated.append('-')
                }
                hyphenated.append(Character.toLowerCase(each))
            } else {
                hyphenated.append(each)
            }
        }
        return hyphenated.toString()
    }
}
