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
import spock.lang.PendingFeature

class MarkDirtyFalseSpec extends Neo4jGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(ClubB, TimestampedB)
    }

    Map getConfiguration() {
        ['grails.gorm.markDirty': false]
    }

    void "verify correct behaviour of version incrementing"() {
        setup:
        def club = new ClubB(name: 'club')
        club.save(flush: true)
        manager.session.clear()

        expect:
        club.version == 0

        when:
        club = ClubB.get(club.id)

        then:
        club.version == 0

        when:
        manager.session.flush()

        then:
        club.version == 0

        when:
        club = ClubB.findByName('club')
        club.save()
        manager.session.flush()
        manager.session.clear()

        then: //non timestamped domains won't increment the version because no properties are dirty
        ClubB.findByName('club').version == 0
    }

    @PendingFeature(reason = "grails.gorm.markDirty:false correctly prevents unnecessary saves for plain properties (see 'version incrementing' above), but Neo4j's session still considers auto-timestamped entities dirty on an unchanged save(), so lastUpdated keeps advancing - a Neo4j dirty-checking gap, not a markDirty config issue")
    def "lastUpdated is updated"() {
        setup:
        new TimestampedB(name: "test").save()
        manager.session.flush()
        manager.session.clear()

        when:
        def ts = TimestampedB.findByName("test")

        then:
        ts.dateCreated != null
        ts.lastUpdated != null
        ts.dateCreated == ts.lastUpdated
        ts.version == 0

        when:
        ts.save()
        manager.session.flush()
        manager.session.clear()
        def newTs = TimestampedB.findByName("test")

        then: //nothing is persisted because nothing was changed
        newTs.lastUpdated == newTs.dateCreated
        newTs.version == 0
    }
}


@Entity
class ClubB implements Neo4jEntity<ClubB> {
    String name
}


@Entity
class TimestampedB implements Neo4jEntity<TimestampedB> {

    String name

    Date dateCreated
    Date lastUpdated
}
