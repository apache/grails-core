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

/**
 * How much a finding of {@code project health} matters, in the order the findings are reported.
 *
 * @since 9.0
 */
@CompileStatic
enum HealthLevel {

    /** As the project stands, its build fails, or a command that builds, tests or runs it does. The command ends with 1. */
    ERROR('Error', 'bold,red'),

    /** Something the upgrade notes require is not done, something that is deprecated for removal is used, or a command does less than it should. With {@code --strict} the command ends with 1. */
    WARNING('Warning', 'bold,yellow'),

    /** What the guide recommends, and the project does not do. It never fails the command. */
    ADVICE('Advice', 'bold,cyan'),

    /** The check could not decide, and says why. It is never taken for a check that found nothing. */
    SKIPPED('Skipped', 'bold,faint'),

    /** The check found nothing. */
    OK('OK', 'bold,green')

    /** The word a line of the report starts with. */
    final String label

    /** The style of the word on a terminal, as picocli marks it up. */
    final String style

    private HealthLevel(String label, String style) {
        this.label = label
        this.style = style
    }

    /**
     * @return the level as the JSON document writes it
     */
    String getWritten() {
        return name().toLowerCase(Locale.ROOT)
    }
}
