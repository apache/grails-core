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

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.hibernate.mapping.ManyToOne
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder

class ManyToOneValuesBinderSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "Test bindManyToOneValues with #scenario"() {
        given:
        // 1. Mock the dependency and use the protected constructor
        def binder = new ManyToOneValuesBinder()

        // 2. Set up mocks for the method arguments
        def association = Mock(HibernateAssociation)
        def manyToOne = new ManyToOne(getGrailsDomainBinder().getMetadataBuildingContext(),null)
        def associatedEntity = Mock(PersistentEntity)

        // 3. Create the config object that the converter will return
        def config = new PropertyConfig()
        if (testFetchMode != null) {
            config.setFetch(testFetchMode)
        }
        config.setLazy(testLazy)
        config.setIgnoreNotFound(testIgnoreNotFound)

        // 4. Define mock behaviors
        association.getMappedForm() >> config
        association.getAssociatedEntity() >> associatedEntity
        associatedEntity.getName() >> "AssociatedEntityName"

        when:
        binder.bindManyToOneValues(association, manyToOne)

        then:
        // 5. Verify that the correct values were set on the ManyToOne object
        manyToOne.getFetchMode() == expectedFetchMode
        manyToOne.isLazy() == expectedLazy
        manyToOne.isIgnoreNotFound() == testIgnoreNotFound
        manyToOne.getReferencedEntityName() == "AssociatedEntityName"

        where:
        scenario                | testFetchMode    | testLazy | testIgnoreNotFound | expectedFetchMode | expectedLazy
        "explicit values"       | FetchMode.JOIN   | true     | true               | FetchMode.JOIN    | true
        "default values"        | null             | null     | false              | FetchMode.DEFAULT | true
        "other explicit values" | FetchMode.SELECT | false    | false              | FetchMode.SELECT  | false
    }
}
