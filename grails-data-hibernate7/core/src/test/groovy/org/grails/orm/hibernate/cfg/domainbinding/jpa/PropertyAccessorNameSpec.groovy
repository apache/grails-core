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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import grails.gorm.tests.DomainOne
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.tests.perf.Author
import grails.gorm.tests.perf.Book
import grails.gorm.tests.perf.BookAuthor
import jakarta.persistence.AccessType
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property

import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty

/**
 * The accessor name a bound property carries: {@code property} by default and {@code field} when the mapping says so; and
 * that the bound model of a datastore carries it, on a property and on the parts of a composite identifier. The entities
 * are existing fixtures, so the scanned entity set of the differential specs is unchanged.
 */
class PropertyAccessorNameSpec extends HibernateGormDatastoreSpec {

    private final PropertyAccessorName accessorName = new PropertyAccessorName()

    void setupSpec() {
        manager.registerDomainClasses(DomainOne, Book, Author, BookAuthor)
    }

    private HibernatePersistentProperty property(Class<?> domainClass, String name) {
        HibernatePersistentEntity entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(domainClass.name)
        return (HibernatePersistentProperty) entity.getPropertyByName(name)
    }

    void "a property is accessed as a property by default and as a field when the mapping says field"() {
        given:
        HibernatePersistentProperty byProperty = property(DomainOne, 'controller')
        HibernatePersistentProperty byField = Spy(property(DomainOne, 'controller'))
        byField.getHibernateMappedForm() >> new PropertyConfig(accessType: AccessType.FIELD)

        expect:
        accessorName.accessorName(byProperty) == 'property'
        accessorName.accessorName(byField) == 'field'
    }

    void "the bound model carries the accessor names the mapping asks for, on a property and on the parts of a composite identifier"() {
        given:
        PersistentClass byProperty = getPersistentEntity(DomainOne).persistentClass
        Component identifier = (Component) getPersistentEntity(BookAuthor).persistentClass.identifier

        expect:
        ((Property) byProperty.getProperty('controller')).propertyAccessorName == 'property'
        identifier.properties*.propertyAccessorName == ['field', 'field']
    }
}
