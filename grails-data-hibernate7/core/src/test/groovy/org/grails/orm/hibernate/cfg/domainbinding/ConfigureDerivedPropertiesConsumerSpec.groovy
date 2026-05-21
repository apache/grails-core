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
import grails.gorm.hibernate.HibernateEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.ConfigureDerivedPropertiesConsumer

class ConfigureDerivedPropertiesConsumerSpec extends HibernateGormDatastoreSpec {

    HibernatePersistentProperty titleProperty

    def setupSpec() {
        manager.addAllDomainClasses([CDPCBook])
    }

    def setup() {
        titleProperty = mappingContext.getPersistentEntity(CDPCBook.name)
            .persistentProperties.find { it.name == 'title' } as HibernatePersistentProperty
    }

    def "should set derived to true if formula is present"() {
        given:
        def mapping = mappingContext.getPersistentEntity(CDPCBook.name).mappedForm
        def propConfig = new org.grails.orm.hibernate.cfg.PropertyConfig()
        propConfig.formula = "upper(title)"
        mapping.columns['title'] = propConfig

        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(titleProperty)

        then:
        propConfig.isDerived() == true
    }

    def "should set derived to false if formula is null"() {
        given:
        def mapping = mappingContext.getPersistentEntity(CDPCBook.name).mappedForm
        def propConfig = new org.grails.orm.hibernate.cfg.PropertyConfig()
        propConfig.formula = null
        mapping.columns['title'] = propConfig

        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(titleProperty)

        then:
        propConfig.isDerived() == false
    }

    def "should do nothing if property configuration is missing"() {
        given:
        def mapping = mappingContext.getPersistentEntity(CDPCBook.name).mappedForm

        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        // use a property name with no PropertyConfig entry
        HibernatePersistentProperty idProp = mappingContext
            .getPersistentEntity(CDPCBook.name).identity as HibernatePersistentProperty

        when:
        consumer.accept(idProp)

        then:
        noExceptionThrown()
    }
}

class CDPCBook implements HibernateEntity<CDPCBook> {
    Long id
    Long version
    String title
}
