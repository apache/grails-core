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

import spock.lang.Specification
import java.beans.PropertyDescriptor
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller
import java.lang.annotation.RetentionPolicy

class HibernateEnumPropertySpec extends Specification {

    def 'HibernateSimpleEnumProperty instantiation and behavior'() {
        given:
        def entity = Mock(PersistentEntity)
        def context = Mock(MappingContext)
        def pd = new PropertyDescriptor('type', EnumEntity, 'getType', 'setType')

        when:
        def prop = new HibernateSimpleEnumProperty(entity, context, pd)

        then:
        prop instanceof HibernateEnumProperty
        prop.isEnum()
        prop.getType() == RetentionPolicy
    }

    def 'HibernateCustomEnumProperty instantiation and behavior'() {
        given:
        def entity = Mock(PersistentEntity)
        def context = Mock(MappingContext)
        def pd = new PropertyDescriptor('type', EnumEntity, 'getType', 'setType')
        def marshaller = Mock(CustomTypeMarshaller)

        when:
        def prop = new HibernateCustomEnumProperty(entity, context, pd, marshaller)

        then:
        prop instanceof HibernateEnumProperty
        prop.isEnum()
        prop.getType() == RetentionPolicy
    }

    static class EnumEntity {

        RetentionPolicy type
    }
}
