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
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Subject

import org.grails.orm.hibernate.cfg.domainbinding.util.ConfigureDerivedPropertiesConsumer

class ConfigureDerivedPropertiesConsumerSpec extends HibernateGormDatastoreSpec {

    def "should set derived to true if formula is present"() {
        given:
        def mapping = Mock(Mapping)
        def propConfig = new PropertyConfig()
        propConfig.setFormula("some SQL formula")
        
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getName() >> "derivedProp"
        
        mapping.getPropertyConfig("derivedProp") >> propConfig
        
        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(persistentProperty)

        then:
        propConfig.isDerived() == true
    }

    def "should set derived to false if formula is null"() {
        given:
        def mapping = Mock(Mapping)
        def propConfig = new PropertyConfig()
        propConfig.setFormula(null)
        
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getName() >> "nonDerivedProp"
        
        mapping.getPropertyConfig("nonDerivedProp") >> propConfig
        
        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(persistentProperty)

        then:
        propConfig.isDerived() == false
    }

    def "should do nothing if property configuration is missing"() {
        given:
        def mapping = Mock(Mapping)
        def persistentProperty = Mock(PersistentProperty)
        persistentProperty.getName() >> "missingProp"
        
        mapping.getPropertyConfig("missingProp") >> null
        
        @Subject
        def consumer = new ConfigureDerivedPropertiesConsumer(mapping)

        when:
        consumer.accept(persistentProperty)

        then:
        noExceptionThrown()
    }
}
