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

package org.grails.forge.build.dependencies

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.ResourceResolver
import org.slf4j.LoggerFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class PomDependencyVersionResolverSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext  = ApplicationContext.run()

    @Shared
    @Subject
    PomDependencyVersionResolver pomDependencyVersionResolver = applicationContext.getBean(PomDependencyVersionResolver)

    void "PomDependencyVersionResolver exposes coordinates map"() {
        expect:
        pomDependencyVersionResolver.coordinates
    }

    void "PomDependencyVersionResolver skips malformed POM resources"() {
        given:
        def malformedPom = File.createTempFile('malformed-pom', '.xml')
        malformedPom.text = '<project><dependencies><dependency></project>'
        def resourceResolver = Mock(ResourceResolver)
        resourceResolver.getResources('classpath:pom.xml') >> [malformedPom.toURI().toURL()].stream()
        Logger logger = (Logger) LoggerFactory.getLogger(PomDependencyVersionResolver)
        def originalLevel = logger.level
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        logger.level = Level.WARN

        when:
        def resolver = new PomDependencyVersionResolver(resourceResolver)

        then:
        resolver.coordinates.isEmpty()
        !resolver.resolve('missing-artifact').present
        appender.list.any {
            it.level == Level.WARN &&
                    it.formattedMessage.contains('Unable to read dependency versions from') &&
                    it.throwableProxy.className == 'org.xml.sax.SAXParseException'
        }

        cleanup:
        logger.detachAppender(appender)
        logger.level = originalLevel
        malformedPom?.delete()
    }
}
