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
package org.grails.datastore.gorm.mongo

import java.util.concurrent.CopyOnWriteArrayList

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import groovy.transform.CompileStatic
import org.slf4j.LoggerFactory

/**
 * Captures what one logger emits for the length of a test, then puts the logger back as it was.
 *
 * <p>The index build logs from background threads, which can keep running while a test reads what was
 * captured and can outlive the datastore that started them. A plain {@link ListAppender} synchronises
 * the append but not the read, so iterating it while a build thread logs can throw
 * {@link java.util.ConcurrentModificationException}. The list here is copy-on-write, so every read sees
 * a stable snapshot.
 */
@CompileStatic
class CapturedLog implements AutoCloseable {

    private final Logger logger

    private final Level previousLevel

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>()

    CapturedLog(String loggerName, Level level) {
        logger = LoggerFactory.getLogger(loggerName) as Logger
        previousLevel = logger.level
        logger.level = level
        appender.list = new CopyOnWriteArrayList<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
    }

    /**
     * @return what has been captured so far
     */
    List<ILoggingEvent> getEvents() {
        new ArrayList<>(appender.list)
    }

    @Override
    void close() {
        logger.detachAppender(appender)
        appender.stop()
        logger.level = previousLevel
    }
}
