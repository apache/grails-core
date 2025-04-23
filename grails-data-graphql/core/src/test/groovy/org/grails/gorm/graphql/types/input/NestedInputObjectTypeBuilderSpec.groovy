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

package org.grails.gorm.graphql.types.input

import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.custom.Circular
import org.grails.gorm.graphql.domain.general.toone.BelongsToHasOne
import org.grails.gorm.graphql.domain.general.toone.Embed
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.entity.property.manager.CompositeId
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.entity.property.manager.EmbeddedEntity
import org.grails.gorm.graphql.types.GraphQLPropertyType

class NestedInputObjectTypeBuilderSpec extends HibernateSpec {

    List<Class> getDomainClasses() { [
            BelongsToHasOne, Embed, CompositeId, EmbeddedEntity, Circular
    ] }

    NestedInputObjectTypeBuilder builder

    void setup() {
        builder = new NestedInputObjectTypeBuilder(new DefaultGraphQLDomainPropertyManager(), null, GraphQLPropertyType.UPDATE)
    }
    /**
     * Not attempting this with a composite keyed entity because
     * embedding a composite keyed identity doesn't work in Hibernate
     * GORM
     */
    void "test timestamps and version are excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(EmbeddedEntity.gormPersistentEntity)

        then:
        props*.name == ['id', 'title']
        props[0].nullable
    }

    void "test associations not the owning side are excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(BelongsToHasOne.gormPersistentEntity)

        then: 'one is excluded because it is not the owning side'
        props*.name == ['id']
    }

    void "test circular associations are excluded"() {
        when:
        List<GraphQLDomainProperty> props = builder.builder.getProperties(Circular.gormPersistentEntity)

        then: 'circular properties are excluded'
        props*.name== ['id', 'otherCircular', 'circulars']
    }

}