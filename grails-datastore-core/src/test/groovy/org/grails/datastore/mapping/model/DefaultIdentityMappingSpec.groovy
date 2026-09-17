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
package org.grails.datastore.mapping.model

import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext

class DefaultIdentityMappingSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity entity = mappingContext.addPersistentEntity(IdMappingWidget)

    void "a lazy identity mapping resolves the identifier through the owning entity"() {
        given:
        ClassMapping classMapping = entity.mapping

        when:
        DefaultIdentityMapping mapping = new DefaultIdentityMapping(classMapping)

        then:
        mapping.classMapping.is(classMapping)
        mapping.generator == ValueGenerator.AUTO
        mapping.identifierName == ['id'] as String[]
        mapping.mappedForm.is(entity.identity.mapping.mappedForm)
        mapping.storedAs == null
    }

    void "a lazy identity mapping without an identity falls back to the default identifier name"() {
        given:
        PersistentEntity embedded = mappingContext.createEmbeddedEntity(IdMappingComponent)

        when:
        DefaultIdentityMapping mapping = new DefaultIdentityMapping(embedded.mapping)

        then:
        embedded.identity == null
        mapping.mappedForm == null
        mapping.identifierName == ['id'] as String[]
        mapping.storedAs == null
    }

    void "an eager identity mapping returns the configured values"() {
        given:
        Property property = new Property(name: 'key')
        property.storedAs = String

        when:
        DefaultIdentityMapping mapping = new DefaultIdentityMapping(entity.mapping, property, ['key'] as String[], ValueGenerator.ASSIGNED)

        then:
        mapping.mappedForm.is(property)
        mapping.identifierName == ['key'] as String[]
        mapping.generator == ValueGenerator.ASSIGNED
        mapping.storedAs == String
    }

    void "the default property mapping exposes the class mapping and mapped form"() {
        given:
        Property property = new Property(name: 'name')
        ClassMapping classMapping = entity.mapping

        when:
        DefaultPropertyMapping mapping = new DefaultPropertyMapping(classMapping, property)

        then:
        mapping.classMapping.is(classMapping)
        mapping.mappedForm.is(property)
    }

    void "the class mapping is created against its entity and context"() {
        when:
        ClassMapping classMapping = entity.mapping

        then:
        classMapping.entity.is(entity)
        classMapping.identifier instanceof IdentityMapping
        classMapping.identifier.identifierName == ['id'] as String[]
        classMapping.mappedForm != null
    }

    void "the identity mapping interface defaults storedAs to null"() {
        given:
        IdentityMapping mapping = new IdentityMapping() {
            String[] getIdentifierName() { ['id'] as String[] }
            ValueGenerator getGenerator() { ValueGenerator.NATIVE }
            ClassMapping getClassMapping() { null }
            Property getMappedForm() { null }
        }

        expect:
        mapping.storedAs == null
    }

    void "value generators cover every supported strategy"() {
        expect:
        ValueGenerator.values()*.name() == ['NATIVE', 'IDENTITY', 'ASSIGNED', 'GENERATED', 'AUTO', 'SEQUENCE', 'HILO',
                                            'SEQHILO', 'INCREMENT', 'UUID', 'UUID2', 'GUID', 'FOREIGN', 'SELECT', 'CUSTOM']
        ValueGenerator.valueOf('SEQUENCE') == ValueGenerator.SEQUENCE
    }

    void "mapping exceptions carry their message and cause"() {
        given:
        Throwable cause = new IllegalStateException('cause')

        expect:
        new IllegalMappingException('bad').message == 'bad'
        new DatastoreConfigurationException('bad').message == 'bad'
        new DatastoreConfigurationException('bad', cause).cause.is(cause)
    }

    void "an embedded persistent entity has no identifier and registers a reflector"() {
        when:
        PersistentEntity embedded = mappingContext.createEmbeddedEntity(IdMappingComponent)

        then:
        embedded instanceof EmbeddedPersistentEntity
        embedded.identity == null
        embedded.initialized
        embedded.reflector != null
        embedded.getPropertyByName('label') != null
    }
}

class IdMappingWidget {
    Long id
    String name
}

class IdMappingComponent {
    String label
}
