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

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.neo4j.mapping.MappingBuilder
import org.grails.datastore.gorm.neo4j.IdGenerator
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.identity.SnowflakeIdGenerator
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by stefan on 10.04.14.
 */
class SnowflakeIdGeneratorSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(Snowman)

    def "snowflake returns different values"() {

        setup:
            def numberOfInvocations = 100000
            def ids = [] as Set

            IdGenerator generator = new SnowflakeIdGenerator()

        when:
            for (def i=0; i<numberOfInvocations; i++) {
                def id = generator.nextId()
                assert !ids.contains(id)
                ids << id
            }

        then:
            ids.size() == numberOfInvocations

    }

    @Rollback
    void "test snowflake generator to save and retrieve an object"() {
        when:
        Snowman snowman=new Snowman(name: "Bob").save(flush:true)
        datastore.currentSession.clear()

        then:
        snowman.id
        Snowman.get(snowman.id)

    }

}

@Entity
class Snowman {

    String name

    static mapping = MappingBuilder.node {
        id(generator:"snowflake")
    }
}
