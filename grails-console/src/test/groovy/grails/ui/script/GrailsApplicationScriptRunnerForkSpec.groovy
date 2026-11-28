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
package grails.ui.script

import java.util.concurrent.TimeUnit

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Lazy
import spock.lang.Specification
import spock.lang.TempDir

import grails.boot.config.GrailsAutoConfiguration
import grails.persistence.support.PersistenceContextInterceptor
import grails.ui.StartedJvmCoverage
import org.apache.grails.gradle.model.cli.JvmOptions

/**
 * {@link GrailsApplicationScriptRunner} as the {@code runScript} task of a build starts it: its
 * {@code main} method in a JVM of its own, in development, where a thread that watches the sources
 * of the application keeps the JVM alive until the application context is closed.
 */
class GrailsApplicationScriptRunnerForkSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    def "scripts run with the application context and the classes of the default package, and the JVM ends"() {
        given: 'two scripts that use a class of the package the application generates code into, without importing it'
        File first = new File(dir, 'first.groovy')
        first.text = 'println "FIRST=" + new AtomicInteger(41).incrementAndGet() + " " + (ctx != null)'
        File second = new File(dir, 'second.groovy')
        second.text = 'println "SECOND=" + new AtomicLong(1).get() + " " + ctx.getBean("grailsApplication").getClass().simpleName'

        when:
        Map result = run(['-Dgrails.codegen.defaultPackage=java.util.concurrent.atomic'], first.path, second.path)

        then:
        result.ended
        result.output.contains('FIRST=42 true')
        result.output.contains('SECOND=1 DefaultGrailsApplication')
        result.exit == 0
    }

    def "a script that fails ends the JVM with a failure"() {
        given:
        File script = new File(dir, 'fails.groovy')
        script.text = 'throw new IllegalStateException("this script fails on purpose")'

        when:
        Map result = run([], script.path)

        then:
        result.ended
        result.output.contains('Script execution error: this script fails on purpose')
        result.exit == 1
    }

    def "a script that does not compile ends the JVM with a failure"() {
        given:
        File script = new File(dir, 'broken.groovy')
        script.text = 'println NoSuchClassAnywhere.name'

        when:
        Map result = run(['-Dgrails.codegen.defaultPackage=java.util.concurrent.atomic'], script.path)

        then:
        result.ended
        result.exit == 1
    }

    def "a failure before any script is run ends the JVM with a failure, and says so"() {
        given:
        File script = new File(dir, 'never.groovy')
        script.text = 'println "NEVER RUN"'

        when: 'what a script runs in cannot be made: an interceptor of the application fails when it is asked for'
        Map result = run([], LateFailingScriptApplication, script.path)

        then: 'the JVM does not wait for the thread that watches the sources of the application'
        result.ended
        result.output.contains('runScript: The scripts could not be run: ')
        result.output.contains('this interceptor fails on purpose')
        !result.output.contains('NEVER RUN')
        result.exit == 1
    }

    private Map run(List<String> jvmArguments, String... scripts) {
        run(jvmArguments, ScriptRunnerApplication, scripts)
    }

    private Map run(List<String> jvmArguments, Class<?> application, String... scripts) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString()]
        command.addAll(1, StartedJvmCoverage.jvmArguments())
        // the options the Grails Gradle plugin gives the task that starts this JVM
        command.addAll(1, JvmOptions.of(Runtime.version().feature()))
        command.addAll(jvmArguments)
        command.add(GrailsApplicationScriptRunner.name)
        command.addAll(scripts.toList())
        command.add(application.name)
        File output = new File(dir, 'output.txt')
        Process process = new ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(output).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        [ended: ended, exit: ended ? process.exitValue() : null, output: output.text]
    }
}

class ScriptRunnerApplication extends GrailsAutoConfiguration {
}

class LateFailingScriptApplication extends GrailsAutoConfiguration {

    @Bean
    @Lazy
    PersistenceContextInterceptor failing() {
        throw new IllegalStateException('this interceptor fails on purpose')
    }
}
