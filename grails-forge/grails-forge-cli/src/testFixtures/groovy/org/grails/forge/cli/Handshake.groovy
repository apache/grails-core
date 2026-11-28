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
package org.grails.forge.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.function.Supplier
import groovy.transform.CompileStatic

/**
 * What a spec and a script that stands in for a program a command starts, the Gradle wrapper of a project mostly,
 * tell each other, so that the spec waits for what the script does and the script for what the spec does, and
 * neither for a time.
 *
 * <p>The script says that it is at a step, and waits at a step until the spec releases it. The channel is a
 * directory of files, which every system has: a step the script said is a file of the directory with a line for each
 * time a script said it, each added whole at the end of the file, and a step the spec released is a file the script
 * looks for. The script is a shell script, which these functions are part of ({@link #getShell}), or a
 * program that writes and reads the files itself ({@link #step} and {@link #releaseFile} name them); a batch file
 * of Windows runs such a program. The spec blocks until a step was said ({@link #awaitStep}), for at most the bound
 * of every wait ({@link SpecWaits#BOUND}), which is not the time it is expected in.</p>
 *
 * <p>A script that waits at a step goes on when the spec releases that step or all of them, or when the directory is
 * gone; {@link #close} releases all of them and removes the directory, so that a script a spec that failed left
 * waiting does not wait for good. The directory is none of the project's, whose files a command may look at.</p>
 */
@CompileStatic
final class Handshake implements Closeable {

    /** What releases every step. */
    static final String ALL = 'all'

    /** The directory the script and the spec write to. */
    final File directory

    /** A handshake in a new directory of temporary files. */
    Handshake() {
        this(Files.createTempDirectory('grailshandshake').toFile())
    }

    /**
     * @param directory the directory the script and the spec write to, which is made where it is not there
     */
    Handshake(File directory) {
        directory.mkdirs()
        this.directory = directory.canonicalFile
    }

    /**
     * The functions of the handshake for a POSIX shell script, to come after its first line:
     * <ul>
     *     <li>{@code say STEP [TEXT...]} says that the script is at the step: it adds a line with the number of the
     *     shell's process and the text at the end of the file of the step, in one write, so that scripts that say a
     *     step at the same time each add their own line. In a part of the script that runs in the background,
     *     {@code $$} is still the script; {@code $!} after it names the background process.</li>
     *     <li>{@code hold STEP} waits until the spec releases the step, or all of them, or the directory is gone.</li>
     * </ul>
     */
    String getShell() {
        String quoted = "'" + directory.path.replace("'", "'\"'\"'") + "'"
        return 'handshake=' + quoted + '\n' +
                // a line added at the end of the file, which a write of a few bytes does whole
                'say() { step=$1; shift; echo "$$ $*" >> "$handshake/$step"; }\n' +
                // a look at the directory ten times a second, which costs the script nothing while it waits
                'hold() { while [ -d "$handshake" ] && [ ! -f "$handshake/release-$1" ] && [ ! -f "$handshake/release-' + ALL + '" ]; do sleep 0.1; done; }\n'
    }

    /** @return the file of a step: a line for each time it was said, each the number of the process and the text */
    File step(String name) {
        return new File(directory, name)
    }

    /** @return the file whose presence releases a step */
    File releaseFile(String name) {
        return new File(directory, "release-${name}")
    }

    /** @return the times the step was said so far, the first first: the whole lines of its file */
    List<Step> said(String name) {
        File file = step(name)
        if (!file.isFile()) {
            return []
        }
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
        // a line that is still being written is not one yet
        String whole = text.substring(0, text.lastIndexOf('\n') + 1)
        return whole.readLines().findAll { String line -> !line.isEmpty() }.collect { String line -> Step.of(name, line) }
    }

    /**
     * Waits until the script has said a step.
     *
     * @return the first time it said it
     */
    Step awaitStep(String name) {
        return awaitStep(name, 1)
    }

    /**
     * Waits until the script has said a step a number of times, as a script that is run again does.
     *
     * @return the time it said it that is the number
     */
    Step awaitStep(String name, int times) {
        return SpecWaits.value("the step ${name} of the script${times > 1 ? " for the ${times}th time" : ''} (${directory})".toString(), { ->
            List<Step> steps = said(name)
            steps.size() >= times ? steps[times - 1] : null
        } as Supplier<Step>)
    }

    /**
     * Waits until the script has said that it has started, which a script says as {@code say started}.
     *
     * @return the number of its process
     */
    long awaitStarted() {
        return awaitStep('started').pid
    }

    /** Releases a step the script waits at, or will wait at. */
    void release(String name) {
        File file = releaseFile(name)
        File part = new File(directory, "${file.name}.part")
        part.text = ''
        Files.move(part.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    /** Releases every step the script waits at, or will wait at. */
    void releaseAll() {
        release(ALL)
    }

    /**
     * Waits until the process that said that it has started has ended.
     */
    void awaitExit() {
        awaitExit(awaitStarted())
    }

    /**
     * Waits until a process has ended, as one the script started does.
     */
    static void awaitExit(long pid) {
        SpecWaits.ended(pid, "the end of the process ${pid}".toString())
    }

    /**
     * Waits until what a command wrote has a line with a text, as what it copies of the script's output does.
     *
     * @return the line
     */
    static String awaitLine(Supplier<? extends CharSequence> output, String text) {
        return SpecWaits.line(output, text)
    }

    @Override
    void close() {
        if (directory.isDirectory()) {
            releaseAll()
            directory.deleteDir()
        }
    }

    /** One time the script said a step. */
    static final class Step {

        /** The step. */
        final String name

        /** The number of the process that said it. */
        final long pid

        /** What the script said with it, without the number. */
        final String text

        private Step(String name, long pid, String text) {
            this.name = name
            this.pid = pid
            this.text = text
        }

        static Step of(String name, String line) {
            int space = line.indexOf(' ')
            String number = space < 0 ? line : line.substring(0, space)
            return new Step(name, number ==~ /[0-9]+/ ? Long.parseLong(number) : -1L, space < 0 ? '' : line.substring(space + 1).trim())
        }

        /** @return the number at a place of the text, as a script says the number of a process it started */
        long number(int index) {
            return Long.parseLong(text.split(/\s+/)[index])
        }

        @Override
        String toString() {
            return "${name}: ${pid} ${text}"
        }
    }
}
