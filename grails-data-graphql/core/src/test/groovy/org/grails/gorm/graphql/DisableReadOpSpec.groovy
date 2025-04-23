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

import grails.gorm.annotation.Entity
import graphql.schema.GraphQLSchema
import org.grails.datastore.gorm.GormEntity
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import spock.lang.Ignore

class DisableReadOpSpec extends HibernateSpec {

    @Override
    List<Class> getDomainClasses() {
        [ReadDisabledEntity]
    }

    // As of graphql-java 15.0, it appears a queryType is required
    @Ignore
    void "test that disable all operation in clean way"() {

        when:
        GraphQLSchema schema = new Schema(hibernateDatastore.mappingContext)
                .generate()

        then:
        schema.queryType.fieldDefinitions.isEmpty()

        and:
        schema.mutationType.fieldDefinitions.size() == 3
        schema.mutationType.fieldDefinitions.find {it.name == "readDisabledEntityCreate"}
        schema.mutationType.fieldDefinitions.find {it.name == "readDisabledEntityDelete"}
        schema.mutationType.fieldDefinitions.find {it.name == "readDisabledEntityUpdate"}
    }
}

@Entity
class ReadDisabledEntity implements GormEntity<ReadDisabledEntity> {

    String prop

    static graphql = GraphQLMapping.build {
        operations.query.enabled false
    }
}
