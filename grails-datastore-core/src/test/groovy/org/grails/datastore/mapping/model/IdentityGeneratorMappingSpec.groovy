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

import grails.gorm.annotation.Entity

import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.document.config.DocumentMappingContext
import org.grails.datastore.mapping.keyvalue.mapping.config.GormKeyValueMappingFactory
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import spock.lang.Specification

/**
 * How the identifier generator named in a mapping block reaches the identity mapping of an entity
 * registered with a mapping context that uses the default identity mapping.
 */
class IdentityGeneratorMappingSpec extends Specification {

    void "a key-value entity mapped with generator #generatorName resolves to #expected and keeps the name"() {
        when:
        PersistentEntity entity = new KeyValueMappingContext('test').addPersistentEntity(entityClass)
        IdentityMapping identifier = entity.mapping.identifier

        then:
        identifier.generator == expected
        identifier.mappedForm.generator == generatorName
        identifier.identifierName == ['id'] as String[]

        where:
        entityClass                    || expected                | generatorName
        GeneratorIdLowerCaseBuiltIn    || ValueGenerator.ASSIGNED | 'assigned'
        GeneratorIdMixedCaseBuiltIn    || ValueGenerator.SEQUENCE | 'Sequence'
        GeneratorIdClassName           || ValueGenerator.CUSTOM   | 'example.CustomIdentifierGenerator'
        GeneratorIdDatastoreStrategy   || ValueGenerator.CUSTOM   | 'snowflake'
    }

    void "a key-value entity that names no generator resolves to AUTO"() {
        when:
        PersistentEntity entity = new KeyValueMappingContext('test').addPersistentEntity(GeneratorIdUnmapped)

        then:
        entity.mapping.identifier.generator == ValueGenerator.AUTO
        entity.mapping.identifier.mappedForm.generator == null
    }

    void "a document entity mapped with a generator class name resolves to CUSTOM and keeps the name"() {
        when:
        PersistentEntity entity = new DocumentMappingContext('test', new ConnectionSourceSettings())
                .addPersistentEntity(GeneratorIdClassName)

        then:
        entity.mapping.identifier.generator == ValueGenerator.CUSTOM
        entity.mapping.identifier.mappedForm.generator == 'example.CustomIdentifierGenerator'
    }

    void "only a generator name that is not built in reaches the custom generator hook of the mapping factory"() {
        given:
        RecordingGeneratorMappingContext context = new RecordingGeneratorMappingContext()

        when:
        PersistentEntity builtIn = context.addPersistentEntity(GeneratorIdMixedCaseBuiltIn)
        PersistentEntity custom = context.addPersistentEntity(GeneratorIdDatastoreStrategy)
        PersistentEntity unmapped = context.addPersistentEntity(GeneratorIdUnmapped)

        then:
        builtIn.mapping.identifier.generator == ValueGenerator.SEQUENCE
        custom.mapping.identifier.generator == ValueGenerator.GENERATED
        unmapped.mapping.identifier.generator == ValueGenerator.AUTO
        context.recordingFactory.resolvedNames == [(GeneratorIdDatastoreStrategy.name): 'snowflake']
    }

    void "the custom generator hook of a mapping factory can reject a generator name"() {
        when:
        new RecordingGeneratorMappingContext().addPersistentEntity(GeneratorIdClassName)

        then:
        DatastoreConfigurationException e = thrown()
        e.message == 'Unknown generator [example.CustomIdentifierGenerator]'
    }
}

class RecordingGeneratorMappingContext extends KeyValueMappingContext {

    RecordingGeneratorMappingContext() {
        super('test')
    }

    @Override
    protected void initializeDefaultMappingFactory(String keyspace) {
        mappingFactory = new RecordingGeneratorMappingFactory(keyspace)
    }

    RecordingGeneratorMappingFactory getRecordingFactory() {
        (RecordingGeneratorMappingFactory) mappingFactory
    }
}

class RecordingGeneratorMappingFactory extends GormKeyValueMappingFactory {

    final Map<String, String> resolvedNames = [:]

    RecordingGeneratorMappingFactory(String keyspace) {
        super(keyspace)
    }

    @Override
    protected ValueGenerator resolveCustomGenerator(ClassMapping classMapping, String generatorName) {
        resolvedNames[classMapping.entity.name] = generatorName
        if (generatorName == 'snowflake') {
            return ValueGenerator.GENERATED
        }
        throw new DatastoreConfigurationException("Unknown generator [${generatorName}]")
    }
}

@Entity
class GeneratorIdLowerCaseBuiltIn {
    String id

    static mapping = {
        id generator: 'assigned'
    }
}

@Entity
class GeneratorIdMixedCaseBuiltIn {
    Long id

    static mapping = {
        id generator: 'Sequence'
    }
}

@Entity
class GeneratorIdClassName {
    String id

    static mapping = {
        id generator: 'example.CustomIdentifierGenerator'
    }
}

@Entity
class GeneratorIdDatastoreStrategy {
    Long id

    static mapping = {
        id generator: 'snowflake'
    }
}

@Entity
class GeneratorIdUnmapped {
    Long id
}
