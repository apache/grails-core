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

import jakarta.persistence.criteria.JoinType
import org.hibernate.resource.jdbc.spi.StatementInspector
import spock.lang.Issue
import spock.lang.Shared

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

@Issue('https://github.com/apache/grails-core/issues/16562')
class CreateAliasJoinSpec extends HibernateGormDatastoreSpec {

    @Shared
    CreateAliasSqlCapture sqlCapture = new CreateAliasSqlCapture()

    void setupSpec() {
        manager.grailsConfig.hibernate.session_factory.statement_inspector = sqlCapture
        manager.registerDomainClasses(CreateAliasTeam, CreateAliasLike)
    }

    void setup() {
        sqlCapture.statements.clear()
    }

    void 'a to-many alias does not multiply projected rows or counts'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def team = new CreateAliasTeam(name: 'team')
                .addToLikes(first).addToLikes(second).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('likes', 'l')
            eq('l.id', first.id)
            isNotNull('l.label')
            projections { id() }
        }

        then:
        rows == [team.id]
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 1

        when:
        def counts = CreateAliasTeam.withCriteria {
            createAlias('likes', 'l')
            eq('l.id', first.id)
            projections { rowCount() }
        }

        then:
        counts == [1L]
    }

    void 'alias projections and ordering reuse the association join'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        new CreateAliasTeam(name: 'team')
                .addToLikes(first).addToLikes(second).save(flush: true, failOnError: true)

        expect:
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'l')
            projections { property('l.label') }
            order('l.label', 'asc')
        } == ['first', 'second']

        CreateAliasTeam.withCriteria {
            createAlias('likes', 'l')
            eq('l.id', first.id)
            projections { property('name') }
            order('l.label', 'asc')
        } == ['team']
    }

    void 'left aliases retain missing associations with alias resolved first: #aliasFirst'() {
        given:
        def favorite = new CreateAliasLike(label: 'favorite').save(failOnError: true)
        new CreateAliasTeam(name: 'with favorite', favorite: favorite).save(failOnError: true)
        new CreateAliasTeam(name: 'without favorite').save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f', 1)
            or {
                isNull('favorite')
                isNotNull('f.label')
            }
            projections { property('name') }
            if (aliasFirst) {
                order('f.label', 'asc')
            }
        }

        then:
        rows.toSet() == ['with favorite', 'without favorite'].toSet()
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 1
        sqlCapture.statements.last() =~ /(?i)\bleft join create_alias_like\b/

        where:
        aliasFirst << [false, true]
    }

    void 'default and explicit inner aliases exclude missing associations'() {
        given:
        def favorite = new CreateAliasLike(label: 'favorite').save(failOnError: true)
        new CreateAliasTeam(name: 'with favorite', favorite: favorite).save(failOnError: true)
        new CreateAliasTeam(name: 'without favorite').save(flush: true, failOnError: true)

        expect:
        CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            isNotNull('f.label')
            projections { property('name') }
        } == ['with favorite']

        CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f', 0)
            isNotNull('f.label')
            projections { property('name') }
        } == ['with favorite']
    }

    void 'right aliases retain unmatched association rows'() {
        given:
        def favorite = new CreateAliasLike(label: 'favorite').save(failOnError: true)
        new CreateAliasLike(label: 'unmatched').save(failOnError: true)
        new CreateAliasTeam(name: 'team', favorite: favorite).save(flush: true, failOnError: true)

        expect:
        CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f', 2)
            projections { property('f.label') }
            order('f.label', 'asc')
        } == ['favorite', 'unmatched']
    }

    void 'detached aliases preserve the configured left join in lists and subqueries'() {
        given:
        def favorite = new CreateAliasLike(label: 'favorite').save(failOnError: true)
        new CreateAliasTeam(name: 'with favorite', favorite: favorite).save(failOnError: true)
        new CreateAliasTeam(name: 'without favorite').save(flush: true, failOnError: true)
        def criteria = new DetachedCriteria(CreateAliasTeam).build {
            join('favorite', JoinType.LEFT)
            createAlias('favorite', 'f')
            or {
                isNull('favorite')
                isNotNull('f.label')
            }
        }

        expect:
        criteria.list()*.name.toSet() == ['with favorite', 'without favorite'].toSet()
        CreateAliasTeam.withCriteria {
            inList('id', criteria.id())
            projections { property('name') }
        }.toSet() == ['with favorite', 'without favorite'].toSet()
    }
}

@Entity
class CreateAliasTeam {
    String name
    CreateAliasLike favorite

    static hasMany = [likes: CreateAliasLike]

    static constraints = {
        favorite nullable: true
    }
}

@Entity
class CreateAliasLike {
    String label
}

class CreateAliasSqlCapture implements StatementInspector {
    final List<String> statements = []

    @Override
    String inspect(String sql) {
        statements.add(sql)
        sql
    }
}