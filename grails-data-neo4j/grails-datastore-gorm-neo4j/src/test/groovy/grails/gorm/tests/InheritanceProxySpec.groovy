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

package grails.gorm.tests


import org.apache.grails.data.neo4j.core.Neo4jGormDatastoreSpec
import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import org.apache.grails.data.testing.tck.domains.Child

class InheritanceProxySpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(Child, ChessClub, PrivateChessClub)
    }

    void "test a proxy is not created"() {
        given:
        new PrivateChessClub(name: "x").save()
        manager.session.flush()
        manager.session.clear()

        when:
        PrivateChessClub club = (PrivateChessClub) ChessClub.findByName("x")

        then:
        club.child == null
        club.baseChild == null
    }
}

@Entity
class ChessClub implements Neo4jEntity<ChessClub> {
    String name
    Child baseChild

    static constraints = {
        baseChild nullable: true
    }
}

@Entity
class PrivateChessClub extends ChessClub implements Neo4jEntity<PrivateChessClub> {
    Child child

    static constraints = {
        child nullable: true
    }
}