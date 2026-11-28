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
package org.apache.grails.gradle.model.cli

import groovy.transform.CompileStatic

/**
 * The options of Java that a JVM which runs a tool is started with, before its class path, whatever starts
 * it: the {@code grails} command line, for a tool that runs as a process of its own, and the Grails Gradle
 * plugin, for the processes of a build, which run the other tools, the scripts, the application and its
 * tests. The two agree on them here.
 *
 * <p>From Java 22 on, the JVM warns on the standard error, in four lines, the first time code of the class
 * path calls a restricted method without native access: JLine, the terminal library of the console of
 * Grails, which a tool can make, calls one where it uses the foreign function API, as it does from Java 22
 * on, and from Java 24 on so does code that loads a native library. A JVM of Java 22 or later is therefore
 * started with {@link #NATIVE_ACCESS}. Java 17 to 21 take the option as well, but have no reason for it.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class JvmOptions {

    /** The option that grants the code of the class path native access. */
    public static final String NATIVE_ACCESS = '--enable-native-access=ALL-UNNAMED'

    /** The first version of Java that is started with {@link #NATIVE_ACCESS}. */
    public static final int NATIVE_ACCESS_SINCE = 22

    private JvmOptions() {
    }

    /**
     * @param javaVersion the feature version of the Java that is started, as 21 or 25; 0 or less when it is
     * not known, which is given nothing it might not take
     * @return the options, in the order they are given
     */
    static List<String> of(int javaVersion) {
        return javaVersion >= NATIVE_ACCESS_SINCE ? [NATIVE_ACCESS] : Collections.<String>emptyList()
    }
}
