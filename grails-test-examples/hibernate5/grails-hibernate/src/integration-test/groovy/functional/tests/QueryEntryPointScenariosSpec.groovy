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

package functional.tests

import spock.lang.PendingFeature
import spock.lang.Specification

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

/**
 * One query through each entry point that builds its own criteria, with an association path next to an alias or a left
 * join, and the rows it must return. The combinations of aliases, blocks and paths are checked in the module test
 * AliasPathModelSpec; these scenarios are about the entry points.
 *
 * The scenarios marked pending are the ones where Hibernate 5 returns something else than Hibernate 7 does; each says why.
 * A pending feature fails once the difference is gone, so the mark is removed then.
 *
 * Data: the club c1 has the team alpha with the likes (a, tag A) and (b, tag B); c2 has beta with (a, tag B); c3 has
 * gamma without likes; c4 has no team.
 */
@Rollback
@Integration(applicationClass = Application)
class QueryEntryPointScenariosSpec extends Specification {

    def setup() {
        QsClub.executeUpdate('delete from QsClub', [:])
        QsLike.executeUpdate('delete from QsLike', [:])
        QsTeam.executeUpdate('delete from QsTeam', [:])
        QsTag.executeUpdate('delete from QsTag', [:])
        QsTag tagA = new QsTag(name: 'A').save(flush: true, failOnError: true)
        QsTag tagB = new QsTag(name: 'B').save(flush: true, failOnError: true)
        QsTeam alpha = new QsTeam(name: 'alpha').save(flush: true, failOnError: true)
        QsTeam beta = new QsTeam(name: 'beta').save(flush: true, failOnError: true)
        QsTeam gamma = new QsTeam(name: 'gamma').save(flush: true, failOnError: true)
        new QsLike(label: 'a', tag: tagA, team: alpha).save(flush: true, failOnError: true)
        new QsLike(label: 'b', tag: tagB, team: alpha).save(flush: true, failOnError: true)
        new QsLike(label: 'a', tag: tagB, team: beta).save(flush: true, failOnError: true)
        new QsClub(name: 'c1', team: alpha).save(flush: true, failOnError: true)
        new QsClub(name: 'c2', team: beta).save(flush: true, failOnError: true)
        new QsClub(name: 'c3', team: gamma).save(flush: true, failOnError: true)
        new QsClub(name: 'c4').save(flush: true, failOnError: true)
    }

    @PendingFeature(reason = 'Hibernate 5 throws "duplicate association path: team.likes" when a where query goes through one path twice (#16905)')
    void "a where query joins an association path once, so one element satisfies both restrictions"() {
        when:
        List<QsClub> clubs = QsClub.where {
            team.likes.label == 'a' && team.likes.tag.name == 'B'
        }.list()

        then: 'only beta has a like that is both a and tagged B; alpha has them on two different likes'
        clubs*.name == ['c2']
    }

    void "a where query with or over two association paths drops a club without one of the associations"() {
        when:
        List<QsClub> clubs = QsClub.where {
            team.name == 'gamma' || team.likes.label == 'b'
        }.list(sort: 'name')
        List<QsClub> criteria = QsClub.createCriteria().list {
            or {
                team { eq 'name', 'gamma' }
                team { likes { eq 'label', 'b' } }
            }
            order 'name'
        }

        then: 'gamma has no likes, so the inner join of the likes drops it, as it does for the criteria builder'
        clubs*.name == ['c1']
        criteria*.name == clubs*.name
    }

    void "a dynamic finder on an association gives the rows of the criteria with an alias on it"() {
        given:
        QsTag tagB = QsTag.findByName('B')

        when:
        List<QsLike> finder = QsLike.findAllByTagAndLabel(tagB, 'a')
        List<QsLike> aliased = QsLike.withCriteria {
            createAlias('tag', 't')
            eq 't.name', 'B'
            eq 'label', 'a'
        }

        then:
        finder*.team*.name == ['beta']
        aliased*.id == finder*.id
    }

    void "a where query with a subquery on an association keeps the clubs whose team is in it"() {
        when:
        List<QsClub> clubs = QsClub.where {
            team.id in QsTeam.where { likes { label == 'b' } }.property('id')
        }.list()

        then:
        clubs*.name == ['c1']
    }

    @PendingFeature(reason = 'Hibernate 5 drops the club without a team for ne on a property of a left alias, as <> is unknown for a null (#16905)')
    void "not over a left-joined association: a missing association matches ne but not not-eq"() {
        when:
        List<QsClub> notEqual = QsClub.withCriteria {
            createAlias('team', 't', 1)
            ne 't.name', 'alpha'
            order 'name'
        }
        List<QsClub> negated = QsClub.withCriteria {
            createAlias('team', 't', 1)
            not { eq 't.name', 'alpha' }
            order 'name'
        }

        then: 'c4 has no team: ne is x <> v or x is null, and not (x = v) is unknown for a null'
        notEqual*.name == ['c2', 'c3', 'c4']
        negated*.name == ['c2', 'c3']
    }

    void "isNull and isNotNull on a property through a left alias"() {
        expect:
        QsClub.withCriteria {
            createAlias('team', 't', 1)
            isNull 't.name'
        }*.name == ['c4']
        QsClub.withCriteria {
            createAlias('team', 't', 1)
            isNotNull 't.name'
            order 'name'
        }*.name == ['c1', 'c2', 'c3']
    }

    @PendingFeature(reason = 'Hibernate 5 list(fetch: join) is an inner join and drops the club without a team (#16853)')
    void "a fetch join keeps the club that has no team"() {
        expect:
        QsClub.list(fetch: [team: 'join'], sort: 'name')*.name == ['c1', 'c2', 'c3', 'c4']
    }
}
