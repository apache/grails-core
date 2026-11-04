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
package org.grails.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

class GrailsCliLoggingVerifier {

    private final String homeDirectory

    GrailsCliLoggingVerifier(String homeDirectory) {
        this.homeDirectory = homeDirectory
    }

    void verify() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory()
        context.reset()
        context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).level = Level.ERROR
        Logger logger = context.getLogger('org.grails.cli.GrailsCli')
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        PrintStream originalErr = System.err
        ByteArrayOutputStream stderr = new ByteArrayOutputStream()
        System.setErr(new PrintStream(stderr, true))
        try {
            System.setProperty('user.home', homeDirectory)
            Class.forName('org.grails.cli.GrailsCli')
            assert appender.list.size() == 1
            ILoggingEvent event = appender.list[0]
            assert event.loggerName == 'org.grails.cli.GrailsCli'
            assert event.level == Level.ERROR
            assert event.formattedMessage.contains('Problem loading')
            assert event.throwableProxy.className.contains('MultipleCompilationErrorsException')
            assert stderr.toString('UTF-8').contains('ERROR: Problem loading')
        }
        finally {
            System.setErr(originalErr)
            logger.detachAppender(appender)
        }
    }
}
