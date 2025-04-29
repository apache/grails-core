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
package org.grails.datastore.gorm.mongo

import com.mongodb.client.MongoDatabase
import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * Created by graemerocher on 01/04/16.
 */
class SetRetrievalSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Team, Player])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/675')
    void "Test retrieve an existing set"() {
        when:"a set is retrieved"
        MongoDatabase db = Team.DB
        db.getCollection('team').insertOne(new Document(name:"Manchester United", nicknames:['Red Devils'] as Set))

        def teams = Team.list()
        then:"the result is correct"
        teams.size() == 1
        teams[0].name == 'Manchester United'
        teams[0].nicknames == ['Red Devils'] as Set
    }

    void "Test persist and retrieve sets"() {
        when:"An object with sets is persisted"
        new Team(name: "Real Madrid", nicknames: ['Los Blancos'] as Set, sports: [Sport.FOOTBALL, Sport.BASKETBALL] as Set ).save(flush:true)
        manager.session.clear()
        List<Team> teams = Team.list()

        then:"It is retrievable"
        teams[0].name == "Real Madrid"
        teams[0].sports == [Sport.FOOTBALL, Sport.BASKETBALL] as Set

    }
}

@Entity
class Team implements MongoEntity<Team> {
    ObjectId id
    String name
    Set<String> nicknames = []
    Set<Sport> sports = []
    static hasMany = [players:Player]
}

@Entity
class Player {
    String name
}

enum Sport {
    FOOTBALL, BASKETBALL
}
