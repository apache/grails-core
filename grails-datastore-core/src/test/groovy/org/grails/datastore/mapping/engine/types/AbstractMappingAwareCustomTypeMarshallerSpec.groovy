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
package org.grails.datastore.mapping.engine.types

import org.springframework.dao.InvalidDataAccessResourceUsageException
import spock.lang.Specification

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.query.Query

class AbstractMappingAwareCustomTypeMarshallerSpec extends Specification {

    RecordingMarshaller marshaller = new RecordingMarshaller()

    private PersistentProperty propertyNamed(String name, String targetName) {
        Stub(PersistentProperty) {
            getName() >> name
            getMapping() >> Stub(PropertyMapping) { getMappedForm() >> new Property(targetName: targetName) }
        }
    }

    void "the marshaller supports every context and exposes its target type"() {
        expect:
        marshaller.targetType == URI
        marshaller.supports(Stub(MappingContext))
        marshaller.supports(Stub(Datastore))
    }

    void "write and read resolve the mapped key before delegating"() {
        given:
        PersistentProperty mapped = propertyNamed('link', 'link_col')
        PersistentProperty plain = propertyNamed('other', null)
        Map target = [:]
        URI uri = new URI('http://example.com')

        when:
        Object written = marshaller.write(mapped, uri, target)
        URI read = marshaller.read(plain, [other: 'http://example.org'])

        then:
        written == 'http://example.com'
        target.link_col == 'http://example.com'
        marshaller.writes == [['link_col', uri]]
        read == new URI('http://example.org')
        marshaller.reads == ['other']
    }

    void "query returns the native query after delegating to queryInternal"() {
        given:
        PersistentProperty mapped = propertyNamed('link', 'link_col')
        Query.PropertyCriterion criterion = new Query.Equals('link', 'x')
        Map nativeQuery = [:]

        when:
        Map result = marshaller.query(mapped, criterion, nativeQuery)

        then:
        result.is(nativeQuery)
        nativeQuery.link_col == 'x'
    }

    void "querying is unsupported by default"() {
        given:
        AbstractMappingAwareCustomTypeMarshaller unqueryable = new AbstractMappingAwareCustomTypeMarshaller<URI, Map, Map>(URI) {
            protected Object writeInternal(PersistentProperty property, String key, URI value, Map nativeTarget) { null }
            protected URI readInternal(PersistentProperty property, String key, Map nativeSource) { null }
        }

        when:
        unqueryable.query(propertyNamed('link', null), new Query.Equals('link', 'x'), [:])

        then:
        InvalidDataAccessResourceUsageException e = thrown()
        e.message == 'Custom type [java.net.URI] does not support querying'
    }

    static class RecordingMarshaller extends AbstractMappingAwareCustomTypeMarshaller<URI, Map, Map> {

        List writes = []
        List reads = []

        RecordingMarshaller() {
            super(URI)
        }

        @Override
        protected Object writeInternal(PersistentProperty property, String key, URI value, Map nativeTarget) {
            writes << [key, value]
            nativeTarget[key] = value.toString()
            value.toString()
        }

        @Override
        protected void queryInternal(PersistentProperty property, String key, Query.PropertyCriterion value, Map nativeQuery) {
            nativeQuery[key] = value.value
        }

        @Override
        protected URI readInternal(PersistentProperty property, String key, Map nativeSource) {
            reads << key
            new URI(nativeSource[key].toString())
        }
    }
}
