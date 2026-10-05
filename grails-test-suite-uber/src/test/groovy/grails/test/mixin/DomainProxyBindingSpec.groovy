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
package grails.test.mixin

import spock.lang.Specification
import spock.lang.Unroll

import grails.artefact.Artefact
import grails.persistence.Entity
import grails.testing.gorm.DataTest
import grails.testing.web.controllers.ControllerUnitTest
import grails.web.databinding.DataBindingUtils
import org.grails.datastore.mapping.proxy.EntityProxy

/**
 * Binds to a proxy of a domain class, a subclass generated at runtime that declares no binding metadata of its own.
 */
class DomainProxyBindingSpec extends Specification implements ControllerUnitTest<DomainProxyBindingController>, DataTest {

    void setupSpec() {
        mockDomain ProxiedBindingRecord
    }

    @Unroll
    void 'a proxy of a domain class binds as the domain class, without id or version, through #binding with secure=#secure'() {
        given:
        grailsApplication.config.grails.databinding.denyByDefault = secure
        // Assigned rather than passed to the constructor, which binds and in secure mode would leave it unset.
        def persisted = new ProxiedBindingRecord()
        persisted.description = 'Persisted'
        Long recordId = persisted.save(flush: true, failOnError: true).id
        ProxiedBindingRecord.withSession { session -> session.clear() }
        params.putAll(recordId: recordId, id: recordId + 100, version: 5, description: 'Rebound')

        when:
        Map bound = binding == 'bindData' ? controller.bindProxy() : bindProxy(recordId)

        then: 'the record was loaded as a proxy'
        bound.proxy

        and:
        bound.id == recordId
        bound.version == 0
        bound.description == expectedDescription

        where:
        binding            | secure || expectedDescription
        'bindData'         | false  || 'Rebound'
        'DataBindingUtils' | false  || 'Rebound'
        'bindData'         | true   || 'Persisted'
        'DataBindingUtils' | true   || 'Persisted'
    }

    private Map bindProxy(Long recordId) {
        def record = ProxiedBindingRecord.load(recordId)
        boolean proxy = record instanceof EntityProxy
        DataBindingUtils.bindObjectToInstance(record, params)
        [proxy: proxy, id: record.id, version: record.version, description: record.description]
    }
}

@Artefact('Controller')
class DomainProxyBindingController {

    def bindProxy(Long recordId) {
        def record = ProxiedBindingRecord.load(recordId)
        boolean proxy = record instanceof EntityProxy
        bindData(record, params)
        [proxy: proxy, id: record.id, version: record.version, description: record.description]
    }
}

@Entity
class ProxiedBindingRecord {
    String description
}
