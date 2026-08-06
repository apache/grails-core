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
package org.apache.grails.core.testing.support

import groovy.transform.CompileStatic

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/**
 * Captures log events emitted by a specific logger during a test.
 *
 * <p>Temporarily sets the logger to the given level (defaults to {@link Level#TRACE}) so that
 * all events are captured, and restores the original logger state when {@link #close()} is called.</p>
 *
 * <p>Typical Spock usage:</p>
 * <pre>
 * given:
 *     def logCapture = new LogCapture(MyClass)
 *     // or by logger name:
 *     def logCapture = new LogCapture('org.some.Logger')
 *
 * then:
 *     logCapture.events.any { it.level == Level.WARN }
 *
 * cleanup:
 *     logCapture.close()
 * </pre>
 */
@CompileStatic
class LogCapture implements AutoCloseable {

    private final Logger logger
    private final Level previousLevel
    private final boolean previousAdditivity
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>()
    private boolean closed

    LogCapture(Class<?> loggerClass, Level level = Level.TRACE) {
        this(LoggerFactory.getLogger(loggerClass) as Logger, level)
    }

    LogCapture(String loggerName, Level level = Level.TRACE) {
        this(LoggerFactory.getLogger(loggerName) as Logger, level)
    }

    private LogCapture(Logger logger, Level level) {
        this.logger = logger
        previousLevel = logger.level
        previousAdditivity = logger.additive
        logger.level = level
        logger.additive = false
        appender.start()
        logger.addAppender(appender)
    }

    /**
     * Returns the log events captured so far.
     *
     * @return list of captured logging events
     */
    List<ILoggingEvent> getEvents() {
        appender.list
    }

    /**
     * Detaches the appender, stops it, and restores the original logger state.
     */
    @Override
    void close() {
        if (closed) {
            return
        }
        closed = true
        logger.detachAppender(appender)
        appender.stop()
        logger.level = previousLevel
        logger.additive = previousAdditivity
    }
}
