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

package org.grails.orm.hibernate.cfg.domainbinding.util

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.MappingException
import org.hibernate.mapping.Component
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import org.hibernate.mapping.BasicValue
import spock.lang.Subject

class GrailsPropertyResolverSpec extends HibernateGormDatastoreSpec {

    @Subject
    GrailsPropertyResolver resolver = new GrailsPropertyResolver()

    void 'should retrieve property directly from PersistentClass'() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName('TestEntity')
        
        Property property = new Property()
        property.setName('testProperty')
        rootClass.addProperty(property)

        when:
        Property result = resolver.getProperty(rootClass, 'testProperty')

        then:
        result == property
    }

    void 'should retrieve property from composite key if not found directly'() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName('TestCompositeEntity')
        
        Table table = new Table('test_table')
        Component compositeKey = new Component(getGrailsDomainBinder().getMetadataBuildingContext(), table, rootClass)
        
        Property keyProperty = new Property()
        keyProperty.setName('keyPart')
        compositeKey.addProperty(keyProperty)
        
        rootClass.setIdentifier(compositeKey)

        when:
        Property result = resolver.getProperty(rootClass, 'keyPart')

        then:
        result == keyProperty
    }

    void 'should throw MappingException if property not found and no composite key fallback'() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName('TestEntity')

        when:
        resolver.getProperty(rootClass, 'nonExistent')

        then:
        thrown(MappingException)
    }

    void 'should throw MappingException if property not found and composite key does not contain it'() {
        given:
        RootClass rootClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        rootClass.setEntityName('TestCompositeEntity')

        Table table = new Table('test_table')
        Component compositeKey = new Component(getGrailsDomainBinder().getMetadataBuildingContext(), table, rootClass)
        rootClass.setIdentifier(compositeKey)

        when:
        resolver.getProperty(rootClass, 'nonExistent')

        then:
        thrown(MappingException)
    }
}
