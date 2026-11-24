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
import org.grails.datastore.mapping.query.Query

/**
 * The rule for the aliases of an association path, through the entry point an application uses: an association is one
 * join however a query spells its path, and an order by an alias-qualified path sorts by that alias. The combinations
 * are checked in the module tests AliasPathIdentitySpec; these are the same rules on the example domain.
 *
 * Data: the club c1 has the team alpha with the likes (a, tag A) and (b, tag B); c2 has beta with (a, tag B); c3 has
 * gamma without likes; c4 has no team.
 */
@Rollback
@Integration(applicationClass = Application)
class AliasRulesScenariosSpec extends Specification {

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

    @PendingFeature(reason = 'a second spelling of a path is a duplicate association path until #16890 reaches 8.0.x')
    void "a path spelled by an alias, a dotted createAlias and a block is one join"() {
        when:
        List<QsClub> clubs = QsClub.createCriteria().list {
            createAlias('team', 'team')
            createAlias('team.likes', 'team_likes')
            createAlias('team_likes.tag', 'first_tag')
            createAlias('team.likes.tag', 'second_tag')
            eq('team_likes.label', 'a')
            eq('first_tag.name', 'B')
            eq('second_tag.name', 'B')
            team {
                likes {
                    tag {
                        eq('name', 'B')
                    }
                }
            }
        }

        then: 'only beta has one like that is both a and tagged B; alpha has them on two different likes'
        clubs*.name == ['c2']
    }

    @PendingFeature(reason = 'a dotted createAlias without an alias for its first association drops the join until #16890 reaches 8.0.x')
    void "a dotted createAlias joins the associations before its last one"() {
        when:
        List<QsClub> clubs = QsClub.createCriteria().list {
            createAlias('team.likes', 'l')
            eq('l.label', 'b')
        }

        then:
        clubs*.name == ['c1']
    }

    @PendingFeature(reason = 'order(Query.Order) does not resolve an alias-qualified path until #16890 reaches 8.0.x')
    void "an order by an alias-qualified path sorts by that alias through both overloads"() {
        when:
        List<QsClub> byString = QsClub.createCriteria().list {
            createAlias('team', 't')
            order('t.name', 'desc')
        }
        List<QsClub> byOrder = QsClub.createCriteria().list {
            createAlias('team', 't')
            order(new Query.Order('t.name', Query.Order.Direction.DESC))
        }

        then: 'the clubs without a team are left out by the inner join'
        byString*.name == ['c3', 'c2', 'c1']
        byOrder*.name == ['c3', 'c2', 'c1']
    }
}
