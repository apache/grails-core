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

package org.grails.gorm.graphql

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.domain.general.description.Annotation
import org.grails.gorm.graphql.domain.hibernate.description.MappingComment
import org.grails.gorm.graphql.domain.general.description.MappingDescription

/**
 * Created by jameskleeh on 7/25/17.
 */
class GraphQLEntityHelperSpec extends HibernateSpec {

    List<Class> getDomainClasses() {
        [Annotation, MappingComment, MappingDescription]
    }

    void "test description from annotation"() {
        given:
        PersistentEntity entity = mappingContext.getPersistentEntity(Annotation.name)

        expect:
        GraphQLEntityHelper.getDescription(entity) == 'Annotation class'
    }

    void "test description from hibernate comment"() {
        given:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingComment.name)

        expect:
        GraphQLEntityHelper.getDescription(entity) == 'MappingComment class'
    }

    void "test description from graphql mapping"() {
        given:
        PersistentEntity entity = mappingContext.getPersistentEntity(MappingDescription.name)

        expect:
        GraphQLEntityHelper.getDescription(entity) == 'MappingDescription class'
    }
}
