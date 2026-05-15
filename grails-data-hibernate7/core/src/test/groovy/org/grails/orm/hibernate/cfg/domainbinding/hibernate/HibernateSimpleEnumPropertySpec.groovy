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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import java.beans.PropertyDescriptor
import java.lang.annotation.RetentionPolicy

class HibernateSimpleEnumPropertySpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([HSEPEntity])
    }

    def 'HibernateSimpleEnumProperty instantiation and behavior'() {
        given:
        def entity = (HibernatePersistentEntity) getMappingContext().getPersistentEntity(HSEPEntity.name)
        def pd = new PropertyDescriptor('type', HSEPEntity, 'getType', 'setType')

        when:
        def prop = new HibernateSimpleEnumProperty(entity, getMappingContext(), pd)

        then:
        prop instanceof HibernateSimpleEnumProperty
        prop instanceof HibernateEnumProperty
        prop.isEnum()
        prop.getType() == RetentionPolicy
    }
}

@Entity
class HSEPEntity {

    Long id
    RetentionPolicy type
}
