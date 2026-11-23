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
package org.apache.grails.forge.cli

import groovy.transform.CompileStatic

/**
 * What the command line says of how it runs a command, when the environment variable
 * {@value #VARIABLE} is {@code true}: which way Gradle is run and why, whether a connection to Gradle
 * was made or used again, whether the classes of the command line were loaded ahead, and how many
 * templates of the feature packs of Forge were compiled while the command ran and how many were taken
 * as the build of Forge compiled them. Each is one line on the standard error, which starts with
 * {@value #PREFIX}, and none is written otherwise.
 *
 * <p>Asking whether it is on reads one field: the environment is read once, when this class is
 * initialized, and what a line says is put together only where it is on.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class Diagnostics {

    /** The environment variable that turns the lines on, with the value {@code true}. */
    public static final String VARIABLE = 'GRAILS_CLI_DIAGNOSTICS'

    /** What every line starts with. */
    public static final String PREFIX = '| Diagnostic '

    private static final boolean ASKED = 'true'.equalsIgnoreCase(System.getenv(VARIABLE)?.trim())

    private static volatile Boolean replaced

    private Diagnostics() {
    }

    /**
     * @return whether the lines are written
     */
    static boolean isOn() {
        Boolean given = replaced
        return given == null ? ASKED : given.booleanValue()
    }

    /**
     * Replaces what the environment says, for a specification, which cannot change the environment it
     * runs in.
     *
     * @param on whether the lines are written; {@code null} for what the environment says
     */
    static void setOn(Boolean on) {
        replaced = on
    }

    /**
     * Writes one line on the standard error, when the lines are on.
     *
     * @param topic what the line is of, as {@code gradle runner}
     * @param text what is said of it
     */
    static void say(String topic, String text) {
        if (!isOn()) {
            return
        }
        PrintStream err = System.err
        err.println(PREFIX + topic + ': ' + text)
        err.flush()
    }
}
