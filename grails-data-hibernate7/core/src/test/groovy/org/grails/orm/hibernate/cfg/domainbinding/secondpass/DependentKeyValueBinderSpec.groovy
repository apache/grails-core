/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.HibernateCompositeIdentity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.hibernate.mapping.DependantValue
import spock.lang.Subject
import org.grails.datastore.mapping.model.PersistentEntity

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH

class DependentKeyValueBinderSpec extends HibernateGormDatastoreSpec {

    SimpleValueBinder simpleValueBinder = Mock(SimpleValueBinder)
    CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = Mock(CompositeIdentifierToManyToOneBinder)

    @Subject
    DependentKeyValueBinder binder = new DependentKeyValueBinder(simpleValueBinder, compositeIdentifierToManyToOneBinder)

    protected HibernateToManyProperty createTestProperty(Class<?> domainClass = TestEntityWithMany) {
        PersistentEntity entity = createPersistentEntity(domainClass)
        return (HibernateToManyProperty) entity.getPropertyByName('items')
    }

    void 'test bind without composite identifier'() {
        given:
        HibernateToManyProperty property = createTestProperty(TestEntityWithMany)
        GrailsHibernatePersistentEntity owner = (GrailsHibernatePersistentEntity) property.getOwner()
        DependantValue key = Mock(DependantValue)

        when:
        binder.bind(property, key)

        then:
        1 * simpleValueBinder.bindSimpleValue(property, null, key, EMPTY_PATH)
        0 * compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(*_)
    }

    void 'test bind with composite identifier and join column support'() {
        given:
        HibernateToManyProperty property = createTestProperty(TestEntityWithCompositeMany)
        def spiedProperty = Spy(property)
        GrailsHibernatePersistentEntity owner = (GrailsHibernatePersistentEntity) spiedProperty.getOwner()
        Mapping mapping = owner.getMappedForm()
        HibernateCompositeIdentity ci = (HibernateCompositeIdentity) mapping.getIdentity()
        DependantValue key = Mock(DependantValue)

        spiedProperty.supportsJoinColumnMapping() >> true // Explicitly force to true for this scenario

        when:
        binder.bind(spiedProperty, key)

        then:
        1 * compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(spiedProperty, key, ci, owner, EMPTY_PATH)
        0 * simpleValueBinder.bindSimpleValue(*_)
    }

    void 'test bind with composite identifier but NO join column support'() {
        given:
        HibernateToManyProperty property = createTestProperty(TestEntityWithCompositeMany)
        def spiedProperty = Spy(property)
        GrailsHibernatePersistentEntity owner = (GrailsHibernatePersistentEntity) spiedProperty.getOwner()
        DependantValue key = Mock(DependantValue)

        spiedProperty.supportsJoinColumnMapping() >> false

        when:
        binder.bind(spiedProperty, key)

        then:
        1 * simpleValueBinder.bindSimpleValue(spiedProperty, null, key, EMPTY_PATH)
        0 * compositeIdentifierToManyToOneBinder.bindCompositeIdentifierToManyToOne(*_)
    }
}

@Entity
class TestEntityWithMany {

    Long id
    String name
    static hasMany = [items: AssociatedItem]
}

@Entity
class AssociatedItem {

    Long id
    String value
    TestEntityWithMany parent
    static belongsTo = [parent: TestEntityWithMany]
}

@Entity
class TestEntityWithCompositeMany {

    Long id
    String name
    static hasMany = [items: AssociatedItemWithComposite]
    static mapping = {
        id composite: ['id', 'name']
    }
}

@Entity
class AssociatedItemWithComposite {

    Long id
    String value
    TestEntityWithCompositeMany parent
    static belongsTo = [parent: TestEntityWithCompositeMany]
}
