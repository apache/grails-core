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
package org.apache.grails.openapi.cli

import java.util.concurrent.TimeUnit

import org.springframework.context.annotation.Bean

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import grails.boot.config.GrailsAutoConfiguration

/**
 * The generate-open-api command declares its options, and is held to them as the {@code runTool} task of a
 * build runs it: by the class that runs a tool, in a JVM of its own, which starts the application around the
 * command. Arguments the command does not declare are refused with exit code 2 before anything is started for
 * it; the options it declares go on to the application, which here fails to start on purpose, so that what is
 * looked at is what the command was given and not what it writes, which {@link GenerateOpenApiToolSpec} covers.
 */
class GenerateOpenApiDeclaredArgumentsSpec extends Specification {

    private static final String RUNNER = 'grails.ui.command.GrailsApplicationContextCommandRunner'

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    @Unroll
    void "#arguments are what the command declares, so the application is started for it"() {
        when:
        Map result = run((['generate-open-api'] + arguments) as String[])

        then:
        result.exit == 1
        result.err.contains('Context failed to load')
        !result.err.contains('| Error generate-open-api')

        where:
        arguments << [
                ['--format', 'json', '--output-directory', 'build/api'],
                ['--format=JSON', '--output-directory=build/api'],
        ]
    }

    @Unroll
    void "#arguments is refused with exit code 2, says #what, and nothing is started for the command"() {
        when:
        Map result = run((['generate-open-api'] + arguments) as String[])

        then:
        result.exit == 2
        result.err.readLines().contains("| Error generate-open-api: ${what}. grails help generate-open-api shows its options".toString())
        !result.err.contains('Context failed to load')

        where:
        arguments                            || what
        ['--formt=json']                     || '--formt is not one of its options'
        ['--output-dir', 'build/api']        || '--output-dir is not one of its options'
        ['--format']                         || '--format needs a value'
        ['build/api']                        || 'it takes no parameters, and was given 1'
    }

    private Map run(String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString()]
        // the JVM that is started is counted as tested: the build says how (the grails-jacoco convention plugin)
        String coverage = System.getProperty('grails.test.fork.jacoco')
        if (coverage) {
            command.add(coverage.replace('@fork@', UUID.randomUUID().toString()))
        }
        command.add(RUNNER)
        command.addAll(arguments.toList())
        command.add(OpenApiFailingApplication.name)
        File out = new File(dir, 'out.txt')
        File err = new File(dir, 'err.txt')
        Process process = new ProcessBuilder(command).directory(dir).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        assert ended
        [exit: process.exitValue(), out: out.text, err: err.text]
    }
}

/**
 * An application whose context fails to start, on purpose.
 */
class OpenApiFailingApplication extends GrailsAutoConfiguration {

    @Bean
    Runnable failing() {
        throw new IllegalStateException('this context fails on purpose')
    }
}
