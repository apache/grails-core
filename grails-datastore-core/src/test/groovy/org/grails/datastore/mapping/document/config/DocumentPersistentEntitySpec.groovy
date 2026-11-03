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
package org.grails.datastore.mapping.document.config

import spock.lang.Specification

import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.JpaMappingConfigurationStrategy

class DocumentPersistentEntitySpec extends Specification {

    void "the document mapping context requires a database name"() {
        when:
        new DocumentMappingContext(null, new ConnectionSourceSettings())

        then:
        IllegalArgumentException e = thrown()
        e.message == 'Argument [defaultDatabaseName] cannot be null'

        when:
        new DocumentMappingContext(null)

        then:
        e = thrown()
        e.message == 'Argument [defaultDatabaseName] cannot be null'

        when:
        new DocumentMappingContext(null, { })

        then:
        e = thrown()
        e.message == 'Argument [defaultDatabaseName] cannot be null'
    }

    void "the document mapping context is initialised from connection source settings"() {
        given:
        ConnectionSourceSettings settings = new ConnectionSourceSettings()
        Closure mapping = { collection 'defaulted' }
        settings.default.mapping = mapping
        settings.default.constraints = { '*' maxSize: 3 }

        when:
        DocumentMappingContext context = new DocumentMappingContext('db', settings)
        PersistentEntity entity = context.addPersistentEntity(DpeBook)

        then:
        context.defaultDatabaseName == 'db'
        context.defaultMapping.is(mapping)
        context.mappingFactory instanceof GormDocumentMappingFactory
        context.mappingSyntaxStrategy instanceof JpaMappingConfigurationStrategy
        entity instanceof DocumentPersistentEntity
        ((Collection) entity.mapping.mappedForm).collection == 'defaulted'
        ((Attribute) entity.getPropertyByName('title').mapping.mappedForm).maxSize == 3
    }

    void "the deprecated constructors build a mapping factory eagerly"() {
        given:
        Closure mapping = { collection 'legacy' }

        when:
        DocumentMappingContext plain = new DocumentMappingContext('db')
        DocumentMappingContext withMapping = new DocumentMappingContext('db', mapping)
        PersistentEntity entity = withMapping.addPersistentEntity(DpeBook)

        then:
        plain.defaultMapping == null
        plain.mappingFactory instanceof GormDocumentMappingFactory
        plain.mappingSyntaxStrategy instanceof JpaMappingConfigurationStrategy
        withMapping.defaultMapping.is(mapping)
        ((Collection) entity.mapping.mappedForm).collection == 'legacy'
    }

    void "document persistent entities expose a collection mapping with a cached identifier"() {
        given:
        DocumentMappingContext context = new DocumentMappingContext('db', new ConnectionSourceSettings())

        when:
        DocumentPersistentEntity entity = context.addPersistentEntity(DpeBook)
        DocumentPersistentEntity.DocumentCollectionMapping mapping = entity.mapping
        IdentityMapping identifier = mapping.identifier

        then:
        !entity.external
        mapping.entity.is(entity)
        mapping.mappedForm instanceof Collection
        mapping.mappedForm.is(context.mappingFactory.createMappedForm(entity))
        identifier.identifierName == ['id'] as String[]
        mapping.identifier.is(identifier)
    }

    void "external document entities have no mapped form"() {
        given:
        DocumentMappingContext context = new DocumentMappingContext('db', new ConnectionSourceSettings())

        when:
        DocumentPersistentEntity entity = context.addExternalPersistentEntity(DpeBook)

        then:
        entity.external
        entity.mapping.mappedForm == null
    }

}

@grails.gorm.annotation.Entity
class DpeBook {

    Long id
    String title

}
