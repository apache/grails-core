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

package grails.gorm.tests.idgeneration

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.neo4j.mapping.MappingBuilder
import org.grails.datastore.gorm.neo4j.IdGenerator
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 07/04/2017.
 */
class CustomIdGeneratorSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(Custom)

    @Rollback
    void "test custom id generator"() {
        when:
        Custom custom =new Custom(age: 10).save(flush:true)
        datastore.currentSession.clear()

        then:
        custom.name == "id0"
        Custom.get(custom.name)
        Custom.findByName(custom.name)
        new DetachedCriteria<>(Custom).idEq(custom.name).find()

    }
}

@Entity
class Custom {
    String name
    Integer age

    static mapping = MappingBuilder.node {
        id(generator:MyGenerator.name, name:"name")
    }
}
class MyGenerator implements IdGenerator {

    int i = 0
    @Override
    Serializable nextId() {
        return "id${i++}"
    }
}