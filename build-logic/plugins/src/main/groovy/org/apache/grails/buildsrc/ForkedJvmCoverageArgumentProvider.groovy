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
package org.apache.grails.buildsrc

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

/**
 * Tells the JVM of a test task how a JVM that a test starts itself is counted by JaCoCo.
 *
 * <p>Several specs run a class the way a build runs it: {@code java ... SomeRunner} as a process of
 * its own, because the class ends the JVM, or because whether the JVM ends is what is tested. The
 * JaCoCo agent of the test task is in the JVM of the tests only, so what such a process ran was
 * reported as code no test ran. The system property {@value #PROPERTY} of the test JVM is the
 * argument that starts the agent in such a process, with {@value #PLACEHOLDER} where the name of
 * the process goes: each process writes an execution data file of its own, into a directory of the
 * test task that the aggregate report reads beside the file of the task. The argument is the one
 * the task itself was given, with its includes and excludes, and another destination; the
 * destination is absolute, since such a process is not started in the directory of the project.</p>
 *
 * <p>A spec starts its process with it like this, and without it where there is no agent (an IDE,
 * a task whose coverage is turned off):</p>
 * <pre>
 * String agent = System.getProperty('grails.test.fork.jacoco')
 * if (agent) {
 *     command.add(agent.replace('&#64;fork&#64;', UUID.randomUUID().toString()))
 * }
 * </pre>
 *
 * <p>Nothing of this is an input of the test task: the paths are those of this checkout, and a
 * test result does not depend on where the coverage of it is written.</p>
 */
@CompileStatic
class ForkedJvmCoverageArgumentProvider implements CommandLineArgumentProvider {

    /** The system property of the test JVM that holds the agent argument for the JVMs it starts. */
    static final String PROPERTY = 'grails.test.fork.jacoco'

    /** What stands for the name of one started JVM in the value of the property. */
    static final String PLACEHOLDER = '@fork@'

    private static final Pattern AGENT = Pattern.compile(/^(-javaagent:.+?=)destfile=[^,]*(.*)$/)

    @Internal
    final JacocoTaskExtension jacoco

    @Internal
    final Provider<Directory> destination

    ForkedJvmCoverageArgumentProvider(JacocoTaskExtension jacoco, Provider<Directory> destination) {
        this.jacoco = jacoco
        this.destination = destination
    }

    @Override
    Iterable<String> asArguments() {
        if (!jacoco.enabled) {
            return []
        }
        String argument = forForkedJvm(jacoco.asJvmArg, destination.get().asFile)
        argument == null ? [] : ["-D${PROPERTY}=${argument}".toString()]
    }

    /**
     * @param ownArgument the agent argument of the test JVM, as {@link JacocoTaskExtension#getAsJvmArg()} gives it
     * @param directory the directory the started JVMs write their execution data into
     * @return the agent argument for a JVM the tests start, or {@code null} when the argument is not one that is understood
     */
    static String forForkedJvm(String ownArgument, File directory) {
        Matcher agent = AGENT.matcher(ownArgument ?: '')
        if (!agent.matches()) {
            return null
        }
        "${agent.group(1)}destfile=${new File(directory, PLACEHOLDER + '.exec').absolutePath}${agent.group(2)}".toString()
    }
}
