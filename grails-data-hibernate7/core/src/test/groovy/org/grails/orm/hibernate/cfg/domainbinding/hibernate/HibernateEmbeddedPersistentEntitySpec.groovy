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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.Mapping

class HibernateEmbeddedPersistentEntitySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HEPEOwner, HEPEAddress])
    }

    private HibernateEmbeddedPersistentEntity getEmbedded() {
        def ctx = getMappingContext() as HibernateMappingContext
        ctx.createEmbeddedEntity(HEPEAddress) as HibernateEmbeddedPersistentEntity
    }

    def "constructor creates embedded entity for given type"() {
        when:
        def embedded = getEmbedded()

        then:
        embedded != null
        embedded.javaClass == HEPEAddress
    }

    def "getMappedForm returns a Mapping instance"() {
        given:
        def embedded = getEmbedded()

        expect:
        embedded.getMappedForm() instanceof Mapping
    }

    def "getMapping returns the class mapping"() {
        given:
        def embedded = getEmbedded()

        expect:
        embedded.getMapping() != null
        embedded.getMapping().getMappedForm() instanceof Mapping
    }

    def "getDataSourceName is null by default"() {
        given:
        def embedded = getEmbedded()

        expect:
        embedded.getDataSourceName() == null
    }

    def "setDataSourceName and getDataSourceName round-trip"() {
        given:
        def embedded = getEmbedded()

        when:
        embedded.setDataSourceName(ConnectionSource.DEFAULT)

        then:
        embedded.getDataSourceName() == ConnectionSource.DEFAULT
    }

    def "getCompositeIdentity returns empty array"() {
        given:
        def embedded = getEmbedded()

        expect:
        embedded.getCompositeIdentity().length == 0
    }

    def "isAbstract returns false"() {
        given:
        def embedded = getEmbedded()

        expect:
        !embedded.isAbstract()
    }

    def "forGrailsDomainMapping returns false"() {
        given:
        def embedded = getEmbedded()

        expect:
        !embedded.forGrailsDomainMapping("default")
    }

    def "getPersistentClass is null by default"() {
        given:
        def embedded = getEmbedded()

        expect:
        embedded.getPersistentClass() == null
    }

    def "usesConnectionSource delegates to ConnectionSourcesSupport"() {
        given:
        def embedded = getEmbedded()

        expect:
        embedded.usesConnectionSource(ConnectionSource.DEFAULT)
    }
}

@Entity
class HEPEOwner {
    Long id
    String name
    HEPEAddress address

    static embedded = ['address']
}

class HEPEAddress {
    String street
    String city
}
