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

import java.util.concurrent.TimeUnit

/**
 * A repository of Git in a directory of a specification, for what the build logic asks Git about.
 */
class GitCheckout {

    final File directory

    GitCheckout(File directory, boolean init = true) {
        this.directory = directory
        directory.mkdirs()
        if (init) {
            git('init', '--quiet')
        }
    }

    /** Whether Git can be run at all: the specifications that need a repository are skipped without it. */
    static boolean isAvailable() {
        try {
            Process process = new ProcessBuilder('git', '--version').redirectErrorStream(true).start()
            process.inputStream.readAllBytes()
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (IOException ignored) {
            return false
        }
    }

    File write(String path, String content) {
        File file = new File(directory, path)
        file.parentFile.mkdirs()
        file.text = content
        return file
    }

    void commit(String message) {
        git('add', '--all')
        git('commit', '--quiet', '--message', message)
    }

    String head() {
        return git('rev-parse', 'HEAD').trim()
    }

    GitCheckout cloneTo(File target) {
        target.parentFile.mkdirs()
        run(target.parentFile, ['git', 'clone', '--quiet', '--depth=1', directory.toPath().toUri().toString(), target.name])
        return new GitCheckout(target, false)
    }

    String git(String... arguments) {
        // the identity and the line endings are given here, so that the machine's configuration decides nothing
        return run(directory, ['git', '-c', 'user.name=Spec', '-c', 'user.email=spec@example.org', '-c', 'core.autocrlf=false'] + arguments.toList())
    }

    private static String run(File directory, List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory).redirectErrorStream(true)
        // Fixture repositories have no dependency on the developer's identity, signing setup or hooks.
        builder.environment().put('GIT_CONFIG_NOSYSTEM', '1')
        builder.environment().put('GIT_CONFIG_GLOBAL', new File(directory, 'absent-git-config').absolutePath)
        Process process = builder.start()
        process.outputStream.close()
        String output = new String(process.inputStream.readAllBytes(), 'UTF-8')
        assert process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0, "${command.join(' ')} failed in ${directory}:\n${output}"
        return output
    }
}
