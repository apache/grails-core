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
package org.apache.grails.cli

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleBuild
import org.apache.grails.forge.cli.console.PlainOutput
import org.apache.grails.forge.cli.console.WrapperFiles

/**
 * What the Grails wrapper of Grails 7 and 8 starts. That wrapper is committed in an application as
 * {@code grails-wrapper.jar} and has the name of this class compiled into it, so it is the one way
 * such an application comes to this command line once its {@code gradle.properties} names Grails 9:
 * the wrapper downloads the command line of that version and starts this class in it.
 *
 * <p>It is the step from that wrapper to the one of this version, and no second entry point: in a
 * Grails project that has a wrapper it runs {@code update-wrapper}, which replaces
 * {@code grailsw}, {@code grailsw.bat} and {@code grails-wrapper.jar} by those of this version, and
 * then runs the command line it was given with {@link GrailsCli}. The wrapper that is written reads
 * the start class from the jar it downloads and does not come here again. Outside a project, and in
 * a project of an earlier version of Grails, nothing is replaced and the command line is run as it
 * is.</p>
 *
 * <p>The earlier wrapper takes {@code update-wrapper} from the front of the command line before it
 * starts this class, so a command line without arguments is what is left of
 * {@code ./grailsw update-wrapper}: where the wrapper was looked at, the JVM ends once that is
 * said, with exit code {@code 0} when the wrapper is that of this version in all its files, or is
 * made so by the wrapper itself when it is next run, and {@code 1} otherwise, and the interactive
 * console is not started.</p>
 *
 * <p>On Windows this class is run with {@code grails-wrapper.jar} open in its JVM and
 * {@code grailsw.bat} being read by the command interpreter, so neither is replaced under it:
 * {@link WrapperFiles} writes the new ones beside them and leaves a batch file that moves them into
 * place when the wrapper is next run. That run starts the wrapper of this version and does not come
 * here again, so the step is taken once and not on every run.</p>
 *
 * @since 9.0
 * @see GrailsCli
 */
@CompileStatic
final class DelegatingShellApplication {

    private DelegatingShellApplication() {
    }

    /**
     * Replaces the wrapper of the project of the current directory, runs the command line and ends
     * the JVM with its exit code.
     *
     * @param args the command line, as the earlier wrapper passes it on
     */
    static void main(String[] args) {
        // Gradle is given the output of the process for as long as it is the output of the command line
        GradleBuild.outputOfProcess(System.out, System.err)
        Integer updated = updateWrapper(args)
        if (updated != null && args.length == 0) {
            System.exit(updated)
        }
        System.exit(GrailsCli.execute(args))
    }

    /**
     * Runs {@code update-wrapper} where it applies.
     *
     * @param args the command line that is run after it
     * @return the exit code of {@code update-wrapper}, or {@code null} where the current directory is
     * in no project whose wrapper is replaced
     */
    static Integer updateWrapper(String... args) {
        if (!WrapperFiles.appliesHere()) {
            return null
        }
        List<String> commandLine = PlainOutput.OPTION in args.takeWhile { String arg -> arg.startsWith('-') } ? [PlainOutput.OPTION] : [] as List<String>
        return GrailsCli.execute((commandLine + [WrapperFiles.COMMAND]) as String[])
    }
}
