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
package org.grails.cli.boot

import java.nio.charset.StandardCharsets

import jakarta.servlet.ServletContext
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

/**
 * Verifies that {@link SpringApplicationWebApplicationInitializer} stays inert when it is
 * discovered on the classpath of a standard {@code bootWar} deployed to an external servlet
 * container, i.e. when the {@code Spring-Application-Source-Classes} manifest entry is absent,
 * while still bootstrapping normally for a CLI-packaged WAR that carries the entry.
 *
 * Regression test for <a href="https://github.com/apache/grails-core/issues/15377">#15377</a>.
 */
class SpringApplicationWebApplicationInitializerSpec extends Specification {

    void "onStartup is inert when the source-classes manifest entry is absent"() {
        given: 'an initializer discovered in a non CLI-packaged WAR'
        def initializer = new RecordingInitializer()
        ServletContext servletContext = Mock(ServletContext) {
            getResourceAsStream('/META-INF/MANIFEST.MF') >> manifestStream
        }

        when: 'the servlet container invokes onStartup'
        initializer.onStartup(servletContext)

        then: 'the application boot is not disturbed and no NPE is raised'
        noExceptionThrown()

        and: 'the CLI bootstrap path is not entered'
        !initializer.bootstrapped

        where: 'the manifest is missing, lacks the entry, or has a blank/whitespace-only entry'
        manifestStream << [
                null,
                manifest(null),
                manifest('Implementation-Title: my-app'),
                manifest('Spring-Application-Source-Classes: '),
                manifest('Spring-Application-Source-Classes:    ')
        ]
    }

    void "onStartup proceeds to the CLI bootstrap path when source classes are present"() {
        given: 'an initializer discovered in a CLI-packaged WAR carrying source classes'
        def initializer = new RecordingInitializer()
        ServletContext servletContext = Mock(ServletContext) {
            getResourceAsStream('/META-INF/MANIFEST.MF') >>
                    manifest('Spring-Application-Source-Classes: com.example.MyApplication')
        }

        when: 'the servlet container invokes onStartup'
        initializer.onStartup(servletContext)

        then: 'the early return is skipped and the initializer bootstraps the application'
        initializer.bootstrapped
    }

    private static InputStream manifest(String entry) {
        StringBuilder text = new StringBuilder('Manifest-Version: 1.0\r\n')
        if (entry != null) {
            text << entry << '\r\n'
        }
        text << '\r\n'
        new ByteArrayInputStream(text.toString().getBytes(StandardCharsets.UTF_8))
    }

    private static class RecordingInitializer extends SpringApplicationWebApplicationInitializer {

        boolean bootstrapped

        @Override
        protected WebApplicationContext createRootApplicationContext(ServletContext servletContext) {
            bootstrapped = true
            null
        }
    }
}
