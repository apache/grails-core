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
package org.grails.datastore.mapping.model.types.mapping

import java.beans.PropertyDescriptor

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.DefaultPropertyMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.types.Association

class PropertyWithMappingSpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity owner = mappingContext.addPersistentEntity(PWMOwner)

    @Shared
    PersistentEntity other = mappingContext.addPersistentEntity(PWMOther)

    private PropertyMapping mappingFor(String name) {
        new DefaultPropertyMapping(owner.mapping, new Property(name: name))
    }

    private static PropertyDescriptor descriptor(String name) {
        new PropertyDescriptor(name, PWMOwner)
    }

    @Unroll
    void "#type.simpleName carries its property mapping"() {
        given:
        PropertyMapping mapping = mappingFor(property)

        when:
        PropertyWithMapping created = type.newInstance(owner, mappingContext, descriptor(property))
        PropertyWithMapping withMapping = type.newInstance(owner, mappingContext, descriptor(property), mapping)

        then:
        created.mapping == null
        withMapping.mapping.is(mapping)
        withMapping.name == property
        withMapping.owner.is(owner)

        when:
        created.mapping = mapping

        then:
        created.mapping.is(mapping)
        created.mappedForm.name == property

        where:
        type                          | property
        SimpleWithMapping             | 'name'
        IdentityWithMapping           | 'id'
        TenantIdWithMapping           | 'tenantId'
        BasicWithMapping              | 'tags'
        EmbeddedWithMapping           | 'other'
        EmbeddedCollectionWithMapping | 'others'
        OneToOneWithMapping           | 'other'
        ManyToOneWithMapping          | 'other'
        OneToManyWithMapping          | 'others'
    }

    void "many-to-many and custom wrappers use their own constructors"() {
        given:
        PropertyMapping mapping = mappingFor('others')

        when:
        ManyToManyWithMapping manyToMany = new ManyToManyWithMapping(owner, mappingContext, descriptor('others'))
        CustomWithMapping custom = new CustomWithMapping(owner, mappingContext, descriptor('name'), null)
        CustomWithMapping customWithMapping = new CustomWithMapping(owner, mappingContext, descriptor('name'), null, mappingFor('name'))

        then:
        manyToMany.mapping == null
        custom.mapping == null
        customWithMapping.mapping.mappedForm.name == 'name'

        when:
        manyToMany.mapping = mapping
        custom.mapping = mappingFor('name')

        then:
        manyToMany.mapping.is(mapping)
        custom.mapping.mappedForm.name == 'name'
        custom.customTypeMarshaller == null
    }

    void "the identity wrapper can be created from a name and type"() {
        when:
        IdentityWithMapping identity = new IdentityWithMapping(owner, mappingContext, 'id', Long)

        then:
        identity.name == 'id'
        identity.type == Long
        identity.mapping == null
    }

    @Unroll
    void "#type.simpleName describes the association as '#prefix'"() {
        given:
        Association association = type.newInstance(owner, mappingContext, descriptor(property))
        association.associatedEntity = other

        expect:
        association.toString() == prefix + PWMOwner.name + '-> ' + property + ' ->' + PWMOther.name

        where:
        type                          | property | prefix
        EmbeddedWithMapping           | 'other'  | 'embedded: '
        EmbeddedCollectionWithMapping | 'others' | 'embedded: '
        OneToOneWithMapping           | 'other'  | 'one-to-one: '
        ManyToOneWithMapping          | 'other'  | 'many-to-one: '
        OneToManyWithMapping          | 'others' | 'one-to-many: '
        ManyToManyWithMapping         | 'others' | 'many-to-many: '
    }
}

class PWMOwner {
    Long id
    String name
    String tenantId
    List<String> tags
    PWMOther other
    Set others
}

class PWMOther {
    Long id
    String name
}
