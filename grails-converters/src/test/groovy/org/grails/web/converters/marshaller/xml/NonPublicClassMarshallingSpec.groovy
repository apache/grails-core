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
package org.grails.web.converters.marshaller.xml

import java.lang.reflect.Modifier

import spock.lang.Specification

import org.springframework.context.ApplicationContext

import grails.converters.XML
import grails.core.DefaultGrailsApplication
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.web.converters.beans.GroovyPersonFactory
import org.grails.web.converters.beans.JavaPersonFactory
import org.grails.web.converters.beans.SerializableJavaBean
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer

/**
 * Marshalling objects whose class is not public (anonymous, inner or package-private) and ordinary
 * {@code Serializable} beans.
 *
 * @see <a href="https://github.com/apache/grails-core/issues/16294">GitHub issue 16294</a>
 * @see <a href="https://github.com/apache/grails-core/issues/16295">GitHub issue 16295</a>
 */
class NonPublicClassMarshallingSpec extends Specification {

    void setup() {
        def initializer = new ConvertersConfigurationInitializer()
        def grailsApplication = new DefaultGrailsApplication()
        grailsApplication.initialise()
        def mappingContext = new KeyValueMappingContext('xml')
        grailsApplication.setApplicationContext(Stub(ApplicationContext) {
            getBean('grailsDomainClassMappingContext', MappingContext) >> mappingContext
        })
        grailsApplication.setMappingContext(mappingContext)
        initializer.grailsApplication = grailsApplication
        initializer.initialize()
    }

    void 'an anonymous Groovy class implementing a public interface is marshalled'() {
        given: 'an anonymous implementation, wrapped in a map so the element name is well defined'
        def person = GroovyPersonFactory.anonymousPerson('user', 42)

        expect: 'the class itself is not public, only its read methods are'
        !Modifier.isPublic(person.getClass().modifiers)

        when:
        String xml = new XML([user: person]).toString()

        then: 'the overridden read methods are invoked, not just the interface default method'
        xml.contains('<name>user</name>')
        xml.contains('<age>42</age>')
        xml.contains('<active>true</active>')

        and: 'the synthetic fields capturing the enclosing variables are not marshalled'
        xml.count('<name>') == 1
        xml.count('<age>') == 1
    }

    void 'a public field of an anonymous Groovy class is marshalled'() {
        given:
        def person = GroovyPersonFactory.anonymousPersonWithPublicField('user', 42, 'nick')

        expect:
        !Modifier.isPublic(person.getClass().modifiers)

        when:
        String xml = new XML([user: person]).toString()

        then: 'the declared public field is marshalled, the synthetic capture fields are not'
        xml.contains('<name>user</name>')
        xml.contains('<nickname>nick</nickname>')
        xml.count('<name>') == 1
        !xml.contains('<nick>')
    }

    void 'a package-private Groovy class implementing a public interface is marshalled'() {
        given:
        def person = GroovyPersonFactory.packagePrivatePerson('user', 42)

        expect:
        !Modifier.isPublic(person.getClass().modifiers)

        when:
        String xml = new XML(person).toString()

        then:
        xml.contains('<name>user</name>')
        xml.contains('<age>42</age>')
        xml.contains('<active>true</active>')
    }

    void 'a package-private Groovy class without any interface is marshalled'() {
        given: 'a read method whose only declaring type is the non-public class itself'
        def person = GroovyPersonFactory.standalonePerson('user')

        expect:
        !Modifier.isPublic(person.getClass().modifiers)

        when:
        String xml = new XML(person).toString()

        then:
        xml.contains('<name>user</name>')
    }

    void 'an anonymous Java class implementing a public interface is marshalled'() {
        given:
        def person = JavaPersonFactory.anonymousPerson('user', 42)

        expect:
        !Modifier.isPublic(person.getClass().modifiers)

        when:
        String xml = new XML([user: person]).toString()

        then:
        xml.contains('<name>user</name>')
        xml.contains('<age>42</age>')
        xml.contains('<active>true</active>')
    }

    void 'a package-private Java class implementing a public interface is marshalled'() {
        given:
        def person = JavaPersonFactory.packagePrivatePerson('user', 42)

        expect:
        !Modifier.isPublic(person.getClass().modifiers)

        when:
        String xml = new XML(person).toString()

        then:
        xml.contains('<name>user</name>')
        xml.contains('<age>42</age>')
        xml.contains('<active>true</active>')
    }

    void 'a package-private Java class without any interface is marshalled'() {
        given:
        def person = JavaPersonFactory.standalonePerson('user')

        expect:
        !Modifier.isPublic(person.getClass().modifiers)

        when:
        String xml = new XML(person).toString()

        then:
        xml.contains('<name>user</name>')
    }

    void 'a Serializable Java bean is marshalled without tripping over its static fields'() {
        given: 'a bean declaring private static final serialVersionUID, as most Java beans do'
        def bean = new SerializableJavaBean('ROLE_ADMIN', 'Administrator')

        when:
        String xml = new XML(bean).toString()

        then: 'the read method and the public instance field are marshalled'
        xml.contains('<authority>ROLE_ADMIN</authority>')
        xml.contains('<label>Administrator</label>')

        and: 'the static fields are not'
        !xml.contains('serialVersionUID')
        !xml.contains('KIND')
    }

    void 'a map of non-public beans and Serializable beans is marshalled as a whole'() {
        given: 'the object graph of the reported reproducer'
        def person = GroovyPersonFactory.anonymousPerson('user', 42)
        def authorities = [new SerializableJavaBean('ROLE_ADMIN', 'Administrator')]

        when:
        String xml = new XML([user: person, authorities: authorities]).toString()

        then:
        xml.contains('<name>user</name>')
        xml.contains('<authority>ROLE_ADMIN</authority>')
    }
}
