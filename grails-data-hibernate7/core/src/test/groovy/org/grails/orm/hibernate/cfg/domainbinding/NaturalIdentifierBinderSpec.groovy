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
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.NaturalId
import org.grails.orm.hibernate.cfg.domainbinding.binder.NaturalIdentifierBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentity
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueNameGenerator
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.UniqueKey

class NaturalIdentifierBinderSpec extends HibernateGormDatastoreSpec {

    void "test bindNaturalIdentifier calls NaturalId.createUniqueKey and handles result"() {
        given:
        def persistentEntity = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def identity = Mock(HibernateIdentity)
        def naturalId = Mock(NaturalId)
        def uk = Mock(UniqueKey)
        def table = Mock(Table)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setTable(table)
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        persistentEntity.getMappedForm() >> mapping
        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.createUniqueKey(rootClass) >> Optional.of(uk)

        when:
        binder.bindNaturalIdentifier(persistentEntity, rootClass)

        then:
        1 * uniqueNameGenerator.setGeneratedUniqueName(uk)
        1 * table.addUniqueKey(uk)
    }

    void "test bindNaturalIdentifier when NaturalId returns empty result"() {
        given:
        def persistentEntity = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def identity = Mock(HibernateIdentity)
        def naturalId = Mock(NaturalId)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        persistentEntity.getMappedForm() >> mapping
        mapping.getIdentity() >> identity
        identity.getNatural() >> naturalId
        naturalId.createUniqueKey(rootClass) >> Optional.empty()

        when:
        binder.bindNaturalIdentifier(persistentEntity, rootClass)

        then:
        0 * uniqueNameGenerator._
    }

    void "test bindNaturalIdentifier when no identity is defined"() {
        given:
        def persistentEntity = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        def uniqueNameGenerator = Mock(UniqueNameGenerator)
        def binder = new NaturalIdentifierBinder(uniqueNameGenerator)

        persistentEntity.getMappedForm() >> mapping
        mapping.getIdentity() >> null

        when:
        binder.bindNaturalIdentifier(persistentEntity, rootClass)

        then:
        0 * uniqueNameGenerator._
    }
}
