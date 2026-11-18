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
package org.grails.cli.compiler.grape

import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import org.eclipse.aether.DefaultRepositorySystemSession
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.repository.LocalRepositoryManager

import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@RestoreSystemProperties
class DefaultRepositorySystemSessionAutoConfigurationSpec extends Specification {

    void 'parallel resolution defaults enable the breadth-first collector and widen the thread pools'() {
        given:
        DefaultRepositorySystemSession session = newSession()

        when:
        new DefaultRepositorySystemSessionAutoConfiguration().apply(session, Stub(RepositorySystem))

        then:
        session.configProperties['aether.dependencyCollector.impl'] == 'bf'
        (session.configProperties['aether.dependencyCollector.bf.threads'] as int) >= 8
        (session.configProperties['aether.metadataResolver.threads'] as int) >= 8
        (session.configProperties['aether.connector.basic.threads'] as int) >= 8
    }

    void 'the thread count is taken from the configurable property when set to a positive integer'() {
        given:
        System.setProperty(DefaultRepositorySystemSessionAutoConfiguration.RESOLUTION_THREADS_PROPERTY, '3')
        DefaultRepositorySystemSession session = newSession()

        when:
        new DefaultRepositorySystemSessionAutoConfiguration().apply(session, Stub(RepositorySystem))

        then:
        session.configProperties['aether.dependencyCollector.bf.threads'] == '3'
        session.configProperties['aether.metadataResolver.threads'] == '3'
        session.configProperties['aether.connector.basic.threads'] == '3'
    }

    void 'a #description property value falls back to the computed default'() {
        given:
        System.setProperty(DefaultRepositorySystemSessionAutoConfiguration.RESOLUTION_THREADS_PROPERTY, value)
        DefaultRepositorySystemSession session = newSession()

        when:
        new DefaultRepositorySystemSessionAutoConfiguration().apply(session, Stub(RepositorySystem))

        then:
        (session.configProperties['aether.connector.basic.threads'] as int) >= 8

        where:
        description         | value
        'zero'              | '0'
        'negative'          | '-4'
        'non-numeric'       | 'lots'
        'blank'             | '   '
    }

    void 'an explicit session configuration is preserved rather than overridden'() {
        given:
        DefaultRepositorySystemSession session = newSession()
        session.setConfigProperty('aether.dependencyCollector.impl', 'df')

        when:
        new DefaultRepositorySystemSessionAutoConfiguration().apply(session, Stub(RepositorySystem))

        then:
        session.configProperties['aether.dependencyCollector.impl'] == 'df'
    }

    private DefaultRepositorySystemSession newSession() {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession()
        // a pre-set local repository manager keeps apply() from needing a real RepositorySystem
        session.setLocalRepositoryManager(Stub(LocalRepositoryManager))
        session
    }
}
