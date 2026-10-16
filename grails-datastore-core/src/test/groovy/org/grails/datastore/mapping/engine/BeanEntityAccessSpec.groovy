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
package org.grails.datastore.mapping.engine

import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

class BeanEntityAccessSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity entity = mappingContext.addPersistentEntity(BEABean)

    void "properties are read and written through a bean wrapper"() {
        given:
        BEABean bean = new BEABean(name: 'n', count: 2, boxed: 3)
        BeanEntityAccess access = new BeanEntityAccess(entity, bean)

        expect:
        access.entity.is(bean)
        access.persistentEntity.is(entity)
        access.getProperty('name') == 'n'
        access.getPropertyValue('count') == 2
        access.getPropertyType('boxed') == Integer
        access.getPropertyType('count') == int

        when:
        access.setProperty('name', 'm')
        access.setProperty('count', 5)

        then:
        bean.name == 'm'
        bean.count == 5
    }

    void "null values are skipped for primitives but applied to reference types"() {
        given:
        BEABean bean = new BEABean(count: 4, boxed: 4)
        BeanEntityAccess access = new BeanEntityAccess(entity, bean)

        when:
        access.setProperty('count', null)
        access.setProperty('boxed', null)

        then:
        bean.count == 4
        bean.boxed == null
    }

    void "a conversion service converts values on write"() {
        given:
        BEABean bean = new BEABean()
        BeanEntityAccess access = new BeanEntityAccess(entity, bean)
        access.conversionService = mappingContext.conversionService

        when:
        access.setProperty('count', '42')
        access.setIdentifier('7')

        then:
        bean.count == 42
        bean.id == 7L
        access.identifier == 7L
        access.identifierName == 'id'
    }

    void "the identifier can be written without conversion"() {
        given:
        BEABean bean = new BEABean()
        BeanEntityAccess access = new BeanEntityAccess(entity, bean)

        when:
        access.setIdentifierNoConversion(9L)
        access.setPropertyNoConversion('name', 'direct')

        then:
        bean.id == 9L
        bean.name == 'direct'
    }

    void "refresh re-applies every readable and writable property"() {
        given:
        BEABean bean = new BEABean(name: 'n', count: 1, boxed: 2)
        BeanEntityAccess access = new BeanEntityAccess(entity, bean)

        when:
        access.refresh()

        then:
        bean.name == 'n'
        bean.count == 1
        bean.boxed == 2
        bean.readOnly == 'ro'
    }

    void "the identifier falls back to the entity identity when the mapping has no identifier name"() {
        given:
        BEABean bean = new BEABean(id: 11L)
        BeanEntityAccess access = new BeanEntityAccess(entity, bean) {
            @Override
            protected String getIdentifierName(org.grails.datastore.mapping.model.ClassMapping cm) {
                null
            }
        }

        expect: 'explicit calls, since the anonymous Groovy subclass routes property syntax through getProperty(String)'
        access.getIdentifier() == 11L
        access.getIdentifierName() == null
    }
}

class BEABean {
    Long id
    String name
    int count
    Integer boxed

    String getReadOnly() {
        'ro'
    }
}
