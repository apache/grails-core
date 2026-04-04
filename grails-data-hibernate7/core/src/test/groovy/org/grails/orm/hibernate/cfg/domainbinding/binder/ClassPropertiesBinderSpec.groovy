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

package org.grails.orm.hibernate.cfg.domainbinding.binder

import org.hibernate.mapping.Table

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Value

class ClassPropertiesBinderSpec extends HibernateGormDatastoreSpec {

    void "test bindClassProperties"() {
        given:
        def grailsPropertyBinder = Mock(GrailsPropertyBinder)
        def propertyFromValueCreator = Mock(PropertyFromValueCreator)
        def naturalIdentifierBinder = Mock(NaturalIdentifierBinder)
        def binder = new ClassPropertiesBinder(grailsPropertyBinder, propertyFromValueCreator, naturalIdentifierBinder)

        def domainClass = Mock(HibernatePersistentEntity)
        def persistentClass = new RootClass(getGrailsDomainBinder().getMetadataBuildingContext())
        persistentClass.setTable(new Table("test"))
        domainClass.getPersistentClass() >> persistentClass
        def mappings = Mock(InFlightMetadataCollector)
        def sessionFactoryBeanName = "sessionFactory"

        def prop1 = Mock(HibernatePersistentProperty)
        prop1.getName() >> "prop1"
        def prop2 = Mock(HibernatePersistentProperty)
        prop2.getName() >> "prop2"
        domainClass.getPersistentPropertiesToBind() >> [prop1, prop2]
        
        def value1 = Mock(Value)
        def value2 = Mock(Value)
        
        def hibernateProp1 = new Property()
        hibernateProp1.setName("hibernateProp1")
        def hibernateProp2 = new Property()
        hibernateProp2.setName("hibernateProp2")
        
        def mapping = Mock(Mapping)
        domainClass.getMappedForm() >> mapping

        when:
        binder.bindClassProperties(domainClass as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity)

        then:
        1 * grailsPropertyBinder.bindProperty(prop1, null, GrailsDomainBinder.EMPTY_PATH) >> value1
        1 * propertyFromValueCreator.createProperty(value1, prop1) >> hibernateProp1

        1 * grailsPropertyBinder.bindProperty(prop2, null, GrailsDomainBinder.EMPTY_PATH) >> value2
        1 * propertyFromValueCreator.createProperty(value2, prop2) >> hibernateProp2

        persistentClass.getProperty("hibernateProp1") == hibernateProp1
        persistentClass.getProperty("hibernateProp2") == hibernateProp2

        1 * naturalIdentifierBinder.bindNaturalIdentifier(domainClass, persistentClass)
    }
}
