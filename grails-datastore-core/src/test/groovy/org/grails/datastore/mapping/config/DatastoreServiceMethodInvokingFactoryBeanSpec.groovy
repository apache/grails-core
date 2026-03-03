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
package org.grails.datastore.mapping.config

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.MultipleConnectionSourceCapableDatastore
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.config.Entity
import grails.gorm.transactions.Transactional
import grails.gorm.services.Service
import spock.lang.Specification

class DatastoreServiceMethodInvokingFactoryBeanSpec extends Specification {

    void "non-multiple connection datastore returns as-is"() {
        given:
        Datastore defaultDatastore = Mock(Datastore)
        def factory = new DatastoreServiceMethodInvokingFactoryBean(SampleService)

        when:
        def result = factory.resolveEffectiveDatastore(defaultDatastore)

        then:
        result.is(defaultDatastore)
    }

    void "multiple connection datastore with no annotations returns default"() {
        given:
        MultipleConnectionSourceCapableDatastore defaultDatastore = Mock(MultipleConnectionSourceCapableDatastore)
        def factory = new DatastoreServiceMethodInvokingFactoryBean(SampleService)

        when:
        def result = factory.resolveEffectiveDatastore(defaultDatastore)

        then:
        result.is(defaultDatastore)
    }

    void "transactional connection resolves custom datastore"() {
        given:
        MultipleConnectionSourceCapableDatastore defaultDatastore = Mock(MultipleConnectionSourceCapableDatastore)
        Datastore resolvedDatastore = Mock(Datastore)
        def factory = new DatastoreServiceMethodInvokingFactoryBean(TxService)

        when:
        def result = factory.resolveEffectiveDatastore(defaultDatastore)

        then:
        1 * defaultDatastore.getDatastoreForConnection("custom") >> resolvedDatastore
        result.is(resolvedDatastore)
    }

    void "transactional connection throws when datastore is missing"() {
        given:
        MultipleConnectionSourceCapableDatastore defaultDatastore = Mock(MultipleConnectionSourceCapableDatastore)
        def factory = new DatastoreServiceMethodInvokingFactoryBean(TxService)

        when:
        factory.resolveEffectiveDatastore(defaultDatastore)

        then:
        1 * defaultDatastore.getDatastoreForConnection("custom") >> null
        ConfigurationException exception = thrown(ConfigurationException)
        exception.message.contains("connection name [custom]")
        exception.message.contains("service [${TxService.name}]")
    }

    void "domain class mapping resolves custom datastore"() {
        given:
        MultipleConnectionSourceCapableDatastore defaultDatastore = Mock(MultipleConnectionSourceCapableDatastore)
        MappingContext mappingContext = Mock(MappingContext)
        PersistentEntity entity = Mock(PersistentEntity)
        ClassMapping classMapping = Mock(ClassMapping)
        Entity mappedForm = new Entity()
        Datastore resolvedDatastore = Mock(Datastore)
        def factory = new DatastoreServiceMethodInvokingFactoryBean(DomainService)

        and:
        defaultDatastore.getMappingContext() >> mappingContext
        mappingContext.getPersistentEntity(SampleDomain.name) >> entity
        entity.getMapping() >> classMapping
        classMapping.getMappedForm() >> mappedForm
        mappedForm.datasources = ["customDomain"]

        when:
        def result = factory.resolveEffectiveDatastore(defaultDatastore)

        then:
        1 * defaultDatastore.getDatastoreForConnection("customDomain") >> resolvedDatastore
        result.is(resolvedDatastore)
    }

    void "domain class mapping throws when datastore is missing"() {
        given:
        MultipleConnectionSourceCapableDatastore defaultDatastore = Mock(MultipleConnectionSourceCapableDatastore)
        MappingContext mappingContext = Mock(MappingContext)
        PersistentEntity entity = Mock(PersistentEntity)
        ClassMapping classMapping = Mock(ClassMapping)
        Entity mappedForm = new Entity()
        def factory = new DatastoreServiceMethodInvokingFactoryBean(DomainService)

        and:
        defaultDatastore.getMappingContext() >> mappingContext
        mappingContext.getPersistentEntity(SampleDomain.name) >> entity
        entity.getMapping() >> classMapping
        classMapping.getMappedForm() >> mappedForm
        mappedForm.datasources = ["customDomain"]

        when:
        factory.resolveEffectiveDatastore(defaultDatastore)

        then:
        1 * defaultDatastore.getDatastoreForConnection("customDomain") >> null
        ConfigurationException exception = thrown(ConfigurationException)
        exception.message.contains("connection name [customDomain]")
        exception.message.contains("domain class [${SampleDomain.name}]")
        exception.message.contains("service [${DomainService.name}]")
    }

    void "transactional DEFAULT connection does not override default datastore"() {
        given:
        MultipleConnectionSourceCapableDatastore defaultDatastore = Mock(MultipleConnectionSourceCapableDatastore)
        def factory = new DatastoreServiceMethodInvokingFactoryBean(TxDefaultService)

        when:
        def result = factory.resolveEffectiveDatastore(defaultDatastore)

        then:
        result.is(defaultDatastore)
    }

    private static class SampleService {
    }

    @Transactional(connection = "custom")
    private static class TxService {
    }

    @Transactional(connection = ConnectionSource.DEFAULT)
    private static class TxDefaultService {
    }

    @Service(SampleDomain)
    static class DomainService {
    }

    static class SampleDomain {
    }
}
