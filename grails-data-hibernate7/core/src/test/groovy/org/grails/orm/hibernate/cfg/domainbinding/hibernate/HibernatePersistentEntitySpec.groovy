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
import org.grails.orm.hibernate.cfg.HibernateSimpleIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.MappingException
import org.hibernate.mapping.RootClass

class HibernatePersistentEntitySpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([HPESimple, HPEComposite])
    }

    def "getIdentity returns null if identity is not HibernatePersistentProperty"() {
        given:
        def entity = getPersistentEntity(HPESimple) as HibernatePersistentEntity
        // Manual override of identity to a non-Hibernate type
        def originalIdentity = entity.identity
        entity.identity = Mock(org.grails.datastore.mapping.model.PersistentProperty)

        expect:
        entity.getIdentity() == null

        cleanup:
        entity.identity = originalIdentity
    }

    def "getCompositeIdentity returns empty array if no composite identity"() {
        given:
        def entity = getPersistentEntity(HPESimple) as HibernatePersistentEntity

        expect:
        entity.getCompositeIdentity().length == 0
    }

    def "getIdentityProperty returns composite property if length > 1"() {
        given:
        def entity = getPersistentEntity(HPEComposite) as HibernatePersistentEntity

        when:
        def idProp = entity.getIdentityProperty()

        then:
        idProp instanceof HibernateCompositeIdentityProperty
    }

    def "getIdentityProperty throws MappingException if no identity"() {
        given: "An entity created manually without registration"
        def entity = new HibernatePersistentEntity(Object, getMappingContext())

        when:
        entity.getIdentityProperty()

        then:
        thrown(MappingException)
    }

    def "getIdentityGeneratorName handles tablePerConcreteClass"() {
        given:
        def entity = getPersistentEntity(HPESimple) as HibernatePersistentEntity
        def identity = entity.getHibernateIdentity() as HibernateSimpleIdentity
        def mapping = entity.getHibernateMappedForm()
        
        // Force generator to 'native' to test the useSequence branch
        def originalGenerator = identity.getGenerator()
        identity.setGenerator('native')
        
        def originalVal = mapping.isTablePerConcreteClass()
        mapping.setTablePerConcreteClass(true)

        expect:
        // When generator is 'native' and tablePerConcreteClass is true, 
        // determineGeneratorName(true) returns 'sequence-identity'.
        entity.getIdentityGeneratorName() == "sequence-identity"

        cleanup:
        mapping.setTablePerConcreteClass(originalVal)
        identity.setGenerator(originalGenerator)
    }

    def "getIdentityGeneratorName throws MappingException for composite identity"() {
        given:
        def entity = getPersistentEntity(HPEComposite) as HibernatePersistentEntity

        when:
        entity.getIdentityGeneratorName()

        then:
        thrown(MappingException)
    }

    def "getRootClass returns root class from persistent class"() {
        given:
        def entity = getPersistentEntity(HPESimple) as HibernatePersistentEntity
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        entity.setPersistentClass(rootClass)

        expect:
        entity.getRootClass().is(rootClass)
    }
}

@Entity
class HPESimple {
    Long id
    String name
}

@Entity
class HPEComposite {
    String a
    String b
    static mapping = {
        id composite: ['a', 'b']
    }
}
