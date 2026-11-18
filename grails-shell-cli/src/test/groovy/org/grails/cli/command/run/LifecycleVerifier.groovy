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
package org.grails.cli.command.run

import java.util.concurrent.TimeUnit
import java.util.logging.Level

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.grails.cli.compiler.GroovyCompilerScope
import org.slf4j.LoggerFactory

class LifecycleVerifier {

    private final String failureMode
    private final boolean loggingEnabled

    LifecycleVerifier(String failureMode, boolean loggingEnabled) {
        this.failureMode = failureMode
        this.loggingEnabled = loggingEnabled
    }

    void verify() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory()
        context.reset()
        Logger rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
        rootLogger.level = loggingEnabled ? ch.qos.logback.classic.Level.DEBUG : ch.qos.logback.classic.Level.OFF
        Logger runnerLogger = context.getLogger(SpringApplicationRunner.name)
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        runnerLogger.addAppender(appender)
        PrintStream originalErr = System.err
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        System.setErr(new PrintStream(stderr, true))
        File source = File.createTempFile('runner-lifecycle', '.groovy')
        try {
            verifyLifecycle(source, stderr, appender)
        }
        finally {
            System.setErr(originalErr)
            runnerLogger.detachAppender(appender)
            source.delete()
        }
    }

    private void verifyLifecycle(File source, ByteArrayOutputStream stderr, ListAppender<ILoggingEvent> appender) {
        source.text = successfulApplicationSource()
        SpringApplicationRunner runner = new SpringApplicationRunner(configuration(failureMode == 'reload'), [source.toURI().toString()] as String[])
        if (failureMode == 'reload') {
            runner.compileAndRun()
            Thread.sleep(1100)
            source.text = invalidApplicationSource()
            assert source.setLastModified(System.currentTimeMillis() + 2000)
            waitForFailure(stderr, appender)
        }
        else {
            source.text = failureMode == 'launch' ? launchFailureSource() : shutdownFailureSource()
            runner.compileAndRun()
            if (failureMode == 'shutdown') {
                runner.stop()
            }
        }
        assertFailure(stderr.toString('UTF-8'), appender.list)
    }

    private void waitForFailure(ByteArrayOutputStream stderr, ListAppender<ILoggingEvent> appender) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline && stderr.size() == 0 && appender.list.empty) {
            Thread.sleep(50)
        }
        assert stderr.size() > 0 || !appender.list.empty
    }

    private void assertFailure(String stderr, List<ILoggingEvent> events) {
        if (loggingEnabled) {
            assert stderr.empty
            assert events.size() == 1
            ILoggingEvent event = events[0]
            assert event.loggerName == SpringApplicationRunner.name
            assert event.level == (failureMode == 'shutdown' ? ch.qos.logback.classic.Level.WARN : ch.qos.logback.classic.Level.ERROR)
            assert event.formattedMessage == expectedMessage()
            assert event.throwableProxy != null
        }
        else {
            assert events.empty
            assert stderr.contains(expectedFailure())
            assert stderr.count(failureHeader()) == 1
        }
    }

    private String expectedMessage() {
        switch (failureMode) {
            case 'launch':
                return 'Unable to launch application'
            case 'reload':
                return 'Unable to compile and run application after a file change'
            default:
                return 'Unable to close application context'
        }
    }

    private String expectedFailure() {
        failureMode == 'reload' ? 'ReloadFailure' : "${failureMode} failure"
    }

    private String failureHeader() {
        failureMode == 'reload' ? 'MultipleCompilationErrorsException' : expectedFailure()
    }

    private SpringApplicationRunnerConfiguration configuration(boolean watch) {
        [
            getScope                  : { GroovyCompilerScope.DEFAULT },
            isGuessImports            : { false },
            isGuessDependencies       : { false },
            isAutoconfigure           : { false },
            getClasspath              : { ['.'] as String[] },
            getRepositoryConfiguration: { [] },
            isQuiet                   : { true },
            isWatchForFileChanges     : { watch },
            getLogLevel               : { loggingEnabled ? Level.INFO : Level.OFF }
        ] as SpringApplicationRunnerConfiguration
    }

    private static String successfulApplicationSource() {
        applicationSource('new Object()')
    }

    private static String launchFailureSource() {
        applicationSource("throw new IllegalStateException('launch failure')")
    }

    private static String shutdownFailureSource() {
        """\
            package org.springframework.boot

            class SpringApplication {
                SpringApplication(Class[] sources) {
                }

                void setDefaultProperties(Map<String, Object> defaultProperties) {
                }

                Object run(String[] args) {
                    new FailingContext()
                }
            }

            class FailingContext {
                void close() {
                    throw new IllegalStateException('shutdown failure')
                }
            }
            """.stripIndent()
    }

    private static String invalidApplicationSource() {
        'class Broken extends ReloadFailure { }\n'
    }

    private static String applicationSource(String runBody) {
        """\
            package org.springframework.boot

            class SpringApplication {
                SpringApplication(Class[] sources) {
                }

                void setDefaultProperties(Map<String, Object> defaultProperties) {
                }

                Object run(String[] args) {
                    ${runBody}
                }
            }
            """.stripIndent()
    }
}
