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
        manager.registerDomainClasses(CreateAliasTeam, CreateAliasLike, CreateAliasStudent, CreateAliasTag, CreateAliasOwner,
                CreateAliasClub)
    }

    void setup() {
        sqlCapture.statements.clear()
    }

    void 'a to-many alias #alias does not multiply projected rows or counts'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def team = new CreateAliasTeam(name: 'team')
                .addToLikes(first).addToLikes(second).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('likes', alias)
            eq("${alias}.id", first.id)
            isNotNull("${alias}.label")
            projections { id() }
        }

        then:
        rows == [team.id]
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 1

        when:
        def counts = CreateAliasTeam.withCriteria {
            createAlias('likes', alias)
            eq("${alias}.id", first.id)
            projections { rowCount() }
        }

        then:
        counts == [1L]

        where:
        alias << ['l', 'likes']
    }

    void 'detached to-many aliases do not multiply counts or paginated total counts'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        new CreateAliasTeam(name: 'both').addToLikes(first).addToLikes(second).save(failOnError: true)
        new CreateAliasTeam(name: 'first only').addToLikes(first).save(failOnError: true)
        new CreateAliasTeam(name: 'second only').addToLikes(second).save(flush: true, failOnError: true)
        def criteria = new DetachedCriteria(CreateAliasTeam).build {
            createAlias('likes', 'l')
            eq('l.label', 'first')
        }

        expect:
        criteria.count() == 2
        criteria.list(max: 1).totalCount == 2
    }

    void 'nested association paths do not reuse a same-named root association join'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        def matching = new CreateAliasTeam(name: 'matching', tag: a)
                .addToLikes(likeB).save(failOnError: true)
        new CreateAliasTeam(name: 'wrong like tag', tag: a).addToLikes(likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'wrong team tag', tag: b).addToLikes(likeB).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            tag { eq('name', 'a') }
            likes { eq('tag.name', 'b') }
        }

        then:
        rows*.id == [matching.id]
    }

    void 'an association block does not hide an explicit alias of the same name declared outside it'() {
        given: 'one team whose favorite has the team tag, and one whose favorite has another'
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'first', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'second', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'same', tag: a, favorite: likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'different', tag: a, favorite: likeB).save(flush: true, failOnError: true)

        when: 'the block compares its own tag with the tag the outer alias names'
        def rows = CreateAliasTeam.withCriteria {
            createAlias('tag', 'tag')
            favorite {
                tag {
                    eqProperty('name', 'tag.name')
                }
            }
        }

        then: 'tag.name is the outer tag, not the tag of the favorite'
        rows*.name == ['same']
    }

    void 'a dotted path in an association block starts at an explicit alias of the same name declared outside it'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'team a, like a', tag: a).addToLikes(likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'team a, like b', tag: a).addToLikes(likeB).save(failOnError: true)
        new CreateAliasTeam(name: 'team b, like a', tag: b).addToLikes(likeA).save(flush: true, failOnError: true)

        when: 'tag.name inside likes names the tag of the outer alias, which is the tag of the team'
        def root = CreateAliasTeam.withCriteria {
            createAlias('tag', 'tag')
            likes { eq('tag.name', 'a') }
        }
        def detached = new DetachedCriteria(CreateAliasTeam).build {
            createAlias('tag', 'tag')
            likes { eq('tag.name', 'a') }
        }.list()
        def subquery = CreateAliasTeam.withCriteria {
            'in'('id', new DetachedCriteria(CreateAliasTeam).build {
                createAlias('tag', 'tag')
                likes { eq('tag.name', 'a') }
            }.id())
        }

        then: 'the root query, a detached criteria and a subquery agree'
        root*.name.sort() == ['team a, like a', 'team a, like b']
        detached*.name.sort() == ['team a, like a', 'team a, like b']
        subquery*.name.sort() == ['team a, like a', 'team a, like b']
    }

    void 'an association block inside a junction does not hide an explicit alias of the same name declared outside it'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'first', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'second', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'same', tag: a, favorite: likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'different', tag: a, favorite: likeB).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('tag', 'tag')
            favorite {
                or {
                    tag {
                        eqProperty('name', 'tag.name')
                    }
                }
            }
        }

        then:
        rows*.name == ['same']
    }

    void 'an association block still joins its association when no alias of that name is declared outside'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'first', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'second', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'with a', tag: b, favorite: likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'with b', tag: a, favorite: likeB).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            favorite {
                tag {
                    eq('name', 'a')
                }
            }
        }

        then:
        rows*.name == ['with a']
    }

    void 'association aliases stay local with root tag #rootTag and explicit join type #explicitJoinType'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'first', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'second', tag: b).save(failOnError: true)
        def entity = rootTag ? CreateAliasTeam : CreateAliasOwner
        def matching = rootTag ? new CreateAliasTeam(name: 'matching', tag: b) : new CreateAliasOwner(name: 'matching')
        def other = rootTag ? new CreateAliasTeam(name: 'other', tag: a) : new CreateAliasOwner(name: 'other')
        matching.addToLikes(likeA).save(failOnError: true)
        other.addToLikes(likeB).save(flush: true, failOnError: true)

        when:
        def rows = entity.withCriteria {
            likes {
                if (explicitJoinType) {
                    createAlias('tag', 't', 0)
                } else {
                    createAlias('tag', 't')
                }
                eq('t.name', 'a')
            }
        }

        then:
        rows*.name == ['matching']

        where:
        rootTag | explicitJoinType
        true    | false
        true    | true
        false   | false
        false   | true
    }

    void 'unused nested aliases materialize on their association with join type #joinType and query #queryType'() {
        given:
        def tag = new CreateAliasTag(name: 'Acme').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: tag).save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        new CreateAliasOwner(name: 'with tag').addToLikes(tagged).save(failOnError: true)
        new CreateAliasOwner(name: 'without tag').addToLikes(untagged).save(flush: true, failOnError: true)
        def criteria = {
            likes {
                createAlias('tag', 't', joinType)
            }
        }
        def builder = CreateAliasOwner.createCriteria()

        when:
        def result
        if (queryType == 'count') {
            result = builder.count(criteria)
        } else if (queryType == 'paginated') {
            result = builder.list(max: 1, sort: 'name', order: 'asc', criteria)
        } else {
            result = builder.list(criteria)
        }

        then:
        if (queryType == 'count') {
            assert result == expectedCount
        } else if (queryType == 'paginated') {
            assert result*.name == ['with tag']
            assert result.totalCount == expectedCount
        } else {
            assert result*.name.toSet() == expectedNames.toSet()
        }

        where:
        joinType | queryType   | expectedCount | expectedNames
        0        | 'list'      | 1             | ['with tag']
        1        | 'list'      | 2             | ['with tag', 'without tag']
        0        | 'count'     | 1             | ['with tag']
        1        | 'count'     | 2             | ['with tag', 'without tag']
        0        | 'paginated' | 1             | ['with tag']
        1        | 'paginated' | 2             | ['with tag', 'without tag']
    }

    void 'association aliases preserve left joins and shadow outer aliases'() {
        given:
        def tag = new CreateAliasTag(name: 'outer').save(failOnError: true)
        def like = new CreateAliasLike(label: 'without tag').save(failOnError: true)
        new CreateAliasTeam(name: 'matching', tag: tag).addToLikes(like).save(flush: true, failOnError: true)

        expect:
        CreateAliasTeam.withCriteria {
            createAlias('tag', 't')
            eq('t.name', 'outer')
            likes {
                createAlias('tag', 't', 1)
                isNull('t.name')
            }
        }*.name == ['matching']
    }

    void 'a left alias inside a disjunction does not override its restrictions'() {
        given:
        def likeA = new CreateAliasLike(label: 'likeA').save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'likeB').save(failOnError: true)
        new CreateAliasTeam(name: 'A', favorite: likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'B', favorite: likeB).save(failOnError: true)
        new CreateAliasTeam(name: 'C', favorite: likeB).save(failOnError: true)
        new CreateAliasTeam(name: 'D').save(failOnError: true)
        new CreateAliasTeam(name: 'E').save(flush: true, failOnError: true)
        def criteria = {
            or {
                createAlias('favorite', 'f', 1)
                isNull('f.id')
                eq('f.label', 'likeB')
            }
        }

        expect:
        CreateAliasTeam.withCriteria(criteria)*.name.toSet() == ['B', 'C', 'D', 'E'].toSet()
        CreateAliasTeam.createCriteria().count(criteria) == 4L

        when:
        def page = CreateAliasTeam.createCriteria().list(max: 2, sort: 'name', order: 'asc', criteria)

        then:
        page*.name == ['B', 'C']
        page.totalCount == 4L
    }

    void 'an empty association block in a disjunction retains its join without overriding restrictions'() {
        given:
        def like = new CreateAliasLike(label: 'like').save(failOnError: true)
        new CreateAliasTeam(name: 'matching').addToLikes(like).save(failOnError: true)
        new CreateAliasTeam(name: 'other').addToLikes(like).save(failOnError: true)
        new CreateAliasTeam(name: 'matching').save(flush: true, failOnError: true)
        def criteria = {
            or {
                likes { }
                eq('name', 'matching')
            }
        }

        expect:
        CreateAliasTeam.withCriteria(criteria)*.name == ['matching']
        CreateAliasTeam.createCriteria().count(criteria) == 1L

        when:
        def page = CreateAliasTeam.createCriteria().list(max: 1, criteria)

        then:
        page*.name == ['matching']
        page.totalCount == 1L
    }

    void 'reusing a builder clears aliases before a new list with pagination #paginated'() {
        given:
        def like = new CreateAliasLike(label: 'favorite').save(failOnError: true)
        new CreateAliasTeam(name: 'with favorite', favorite: like).save(failOnError: true)
        new CreateAliasTeam(name: 'without favorite').save(flush: true, failOnError: true)
        def builder = CreateAliasTeam.createCriteria()

        expect:
        builder.list {
            createAlias('favorite', 'f')
        }*.name == ['with favorite']

        when:
        def rows = paginated ? builder.list(max: 10) { } : builder.list { }

        then:
        rows*.name.toSet() == ['with favorite', 'without favorite'].toSet()
        !paginated || rows.totalCount == 2

        where:
        paginated << [false, true]
    }

    void 'an entity collection alias preserves size restrictions without multiplying rows'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        new CreateAliasTeam(name: 'two').addToLikes(first).addToLikes(second).save(failOnError: true)
        new CreateAliasTeam(name: 'one').addToLikes(first).save(failOnError: true)
        new CreateAliasTeam(name: 'empty').save(flush: true, failOnError: true)

        when:
        def names = CreateAliasTeam.withCriteria {
            createAlias('likes', 'l')
            eq('l.id', first.id)
            sizeEq('likes', 2)
            projections { property('name') }
        }

        then:
        names == ['two']
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 1
    }

    void 'a to-many alias uses a single association join when selecting entities'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def team = new CreateAliasTeam(name: 'team')
                .addToLikes(first).addToLikes(second).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('likes', 'l')
            eq('l.id', first.id)
        }

        then:
        rows*.id == [team.id]
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 1
    }

    void 'entity collection aliases #firstAlias and #secondAlias match different elements with reverse order #reverseOrder'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def team = new CreateAliasTeam(name: 'both').addToLikes(first).addToLikes(second).save(failOnError: true)
        new CreateAliasTeam(name: 'first only').addToLikes(first).save(failOnError: true)
        new CreateAliasTeam(name: 'second only').addToLikes(second).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('likes', firstAlias)
            createAlias('likes', secondAlias)
            if (reverseOrder) {
                eq("${secondAlias}.label", 'second')
                eq("${firstAlias}.label", 'first')
            } else {
                eq("${firstAlias}.label", 'first')
                eq("${secondAlias}.label", 'second')
            }
            projections { id() }
        }

        then:
        rows == [team.id]
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 2

        where:
        firstAlias | secondAlias | reverseOrder
        'a'        | 'b'         | false
        'a'        | 'b'         | true
        'likes'    | 'other'     | false
        'likes'    | 'other'     | true
        'other'    | 'likes'     | false
        'other'    | 'likes'     | true
    }

    void 'unreferenced to-one aliases retain the inner join with left first #leftFirst and projection #projectNames'() {
        given:
        def favorite = new CreateAliasLike(label: 'favorite').save(failOnError: true)
        new CreateAliasTeam(name: 'with favorite', favorite: favorite).save(failOnError: true)
        new CreateAliasTeam(name: 'without favorite').save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'a', leftFirst ? 1 : 0)
            createAlias('favorite', 'b', leftFirst ? 0 : 1)
            if (projectNames) {
                projections { property('name') }
            }
        }

        then:
        (projectNames ? rows : rows*.name) == ['with favorite']
        // Hibernate may omit the unused to-one LEFT join, but the INNER join must reach SQL.
        sqlCapture.statements.last() =~ /(?i)(?<!left )\bjoin create_alias_like\b/

        where:
        leftFirst | projectNames
        true      | false
        true      | true
        false     | false
        false     | true
    }

    void 'unreferenced mixed aliases survive counts and pagination'() {
        given:
        def favorite = new CreateAliasLike(label: 'favorite').save(failOnError: true)
        new CreateAliasTeam(name: 'with favorite', favorite: favorite).save(failOnError: true)
        new CreateAliasTeam(name: 'without favorite').save(flush: true, failOnError: true)

        when:
        def count = CreateAliasTeam.createCriteria().count {
            createAlias('favorite', 'a', 1)
            createAlias('favorite', 'b', 0)
        }

        then:
        count == 1
        sqlCapture.statements.last() =~ /(?i)(?<!left )\bjoin create_alias_like\b/

        when:
        def rows = CreateAliasTeam.createCriteria().list(max: 1) {
            createAlias('favorite', 'a', 1)
            createAlias('favorite', 'b', 0)
            order('name', 'asc')
        }

        then:
        rows*.name == ['with favorite']
        rows.totalCount == 1
        sqlCapture.statements.last() =~ /(?i)(?<!left )\bjoin create_alias_like\b/
    }

    void 'an inner entity alias does not inherit the later left alias join type'() {
        given:
        def like = new CreateAliasLike(label: 'first').save(failOnError: true)
        new CreateAliasTeam(name: 'with likes').addToLikes(like).save(failOnError: true)
        new CreateAliasTeam(name: 'without likes').save(flush: true, failOnError: true)

        when:
        def names = CreateAliasTeam.withCriteria {
            createAlias('likes', 'a')
            createAlias('likes', 'b', 1)
            isNull('a.label')
            projections { property('name') }
        }

        then:
        names == []
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 2
        sqlCapture.statements.last().findAll(/(?i)\bleft join create_alias_team_create_alias_like\b/).size() == 1
    }

    void 'entity aliases retain mixed join types with left first #leftFirst and projected aliases #projectAliases'() {
        given:
        def like = new CreateAliasLike(label: 'first').save(failOnError: true)
        new CreateAliasTeam(name: 'with likes').addToLikes(like).save(failOnError: true)
        new CreateAliasTeam(name: 'without likes').save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            if (leftFirst) {
                createAlias('likes', 'a', 1)
                createAlias('likes', 'b')
            } else {
                createAlias('likes', 'a')
                createAlias('likes', 'b', 1)
            }
            or {
                isNull('a.label')
                eq('a.label', 'first')
            }
            or {
                isNull('b.label')
                eq('b.label', 'first')
            }
            projections {
                property('name')
                if (projectAliases) {
                    property('a.label')
                    property('b.label')
                }
            }
            if (projectAliases) {
                order('b.label', 'asc')
                order('a.label', 'asc')
            }
        }

        then:
        if (projectAliases) {
            assert rows*.toList() == [['with likes', 'first', 'first']]
        } else {
            assert rows == ['with likes']
        }
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 2
        sqlCapture.statements.last().findAll(/(?i)\bleft join create_alias_team_create_alias_like\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bleft join create_alias_like\b/).size() == 1

        where:
        leftFirst | projectAliases
        false     | false
        true      | false
        false     | true
        true      | true
    }

    void 'entity collection aliases survive pagination counts with offset #offset'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        new CreateAliasTeam(name: 'both A').addToLikes(first).addToLikes(second).save(failOnError: true)
        new CreateAliasTeam(name: 'both B').addToLikes(first).addToLikes(second).save(failOnError: true)
        new CreateAliasTeam(name: 'first only').addToLikes(first).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.createCriteria().list(max: 1, offset: offset) {
            createAlias('likes', 'a')
            createAlias('likes', 'b')
            eq('a.label', 'first')
            eq('b.label', 'second')
            order('name', 'asc')
        }

        then:
        rows*.name == expectedNames
        rows.totalCount == 2
        rows.totalCount == 2 // provides a small check that repeated access returns the same value

        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 2

        where:
        offset | expectedNames
        0      | ['both A']
        1      | ['both B']
        2      | []
    }

    void 'entity collection aliases survive count with projection #projectNames'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        new CreateAliasTeam(name: 'both').addToLikes(first).addToLikes(second).save(failOnError: true)
        new CreateAliasTeam(name: 'first only').addToLikes(first).save(flush: true, failOnError: true)

        when:
        def count = CreateAliasTeam.createCriteria().count {
            createAlias('likes', 'a')
            createAlias('likes', 'b')
            eq('a.label', 'first')
            eq('b.label', 'second')
            if (projectNames) {
                projections { property('name') }
            }
        }

        then:
        count == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 2

        where:
        projectNames << [false, true]
    }

    void 'basic collection aliases survive pagination counts'() {
        given:
        new CreateAliasStudent(name: 'A').addToSchools('MIT').addToSchools('Harvard').save(failOnError: true)
        new CreateAliasStudent(name: 'B').addToSchools('MIT').save(failOnError: true)
        new CreateAliasStudent(name: 'C').addToSchools('Harvard').save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasStudent.createCriteria().list(max: 1, offset: 1) {
            createAlias('schools', 's')
            eq('s', 'MIT')
            order('name', 'asc')
        }

        then:
        rows*.name == ['B']
        rows.totalCount == 2
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_student_schools\b/).size() == 1
    }

    void 'basic collection aliases survive count with projection #projectNames'() {
        given:
        new CreateAliasStudent(name: 'A').addToSchools('MIT').addToSchools('Harvard').save(failOnError: true)
        new CreateAliasStudent(name: 'B').addToSchools('MIT').save(failOnError: true)
        new CreateAliasStudent(name: 'C').addToSchools('Harvard').save(flush: true, failOnError: true)

        when:
        def count = CreateAliasStudent.createCriteria().count {
            createAlias('schools', 's')
            eq('s', 'MIT')
            if (projectNames) {
                projections { property('name') }
            }
        }

        then:
        count == 2
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_student_schools\b/).size() == 1

        where:
        projectNames << [false, true]
    }

    void 'an alias named after another association uses its declared path with reverse order #reverseOrder'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def team = new CreateAliasTeam(name: 'matching', favorite: first)
                .addToLikes(second).save(failOnError: true)
        new CreateAliasTeam(name: 'favorite only', favorite: first)
                .addToLikes(first).save(failOnError: true)
        new CreateAliasTeam(name: 'likes only', favorite: second)
                .addToLikes(second).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            createAlias('likes', 'favorite')
            if (reverseOrder) {
                eq('favorite.label', 'second')
                eq('f.label', 'first')
            } else {
                eq('f.label', 'first')
                eq('favorite.label', 'second')
            }
            projections {
                id()
                property('f.label')
                property('favorite.label')
            }
            order('favorite.label', 'asc')
            order('f.label', 'asc')
        }

        then:
        rows*.toList() == [[team.id, 'first', 'second']]
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 2

        where:
        reverseOrder << [false, true]
    }

    void 'an alias colliding with a path works with only a projection or order: #projection'() {
        given:
        def x = new CreateAliasLike(label: 'x').save(failOnError: true)
        def y = new CreateAliasLike(label: 'y').save(failOnError: true)
        new CreateAliasTeam(name: 'T1', favorite: x).addToLikes(y).save(failOnError: true)
        new CreateAliasTeam(name: 'T2', favorite: y).addToLikes(x).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            createAlias('likes', 'favorite')
            eq('favorite.label', 'y')
            if (projection) {
                projections {
                    property('f.label')
                    property('name')
                }
            } else {
                order('f.label', 'asc')
            }
        }

        then:
        if (projection) {
            assert rows*.toList() == [['x', 'T1']]
        } else {
            assert rows*.name == ['T1']
        }
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 2

        where:
        projection << [true, false]
    }

    void 'association blocks remain path based when an alias has the same name: #projectAlias'() {
        given:
        def x = new CreateAliasLike(label: 'x').save(failOnError: true)
        def y = new CreateAliasLike(label: 'y').save(failOnError: true)
        new CreateAliasTeam(name: 'T1', favorite: x).addToLikes(y).save(failOnError: true)
        new CreateAliasTeam(name: 'T2', favorite: y).addToLikes(y).save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            createAlias('likes', 'favorite')
            favorite {
                eq('label', 'x')
            }
            eq('favorite.label', 'y')
            if (projectAlias) {
                projections { property('favorite.label') }
            } else {
                projections { property('name') }
            }
        }

        then:
        if (projectAlias) {
            assert rows == ['y']
        } else {
            assert rows == ['T1']
        }
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team_create_alias_like\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 2

        where:
        projectAlias << [true, false]
    }

    void 'distinct aliases of a basic collection match different elements using explicit inner join #explicitInner'() {
        given:
        def student = new CreateAliasStudent(name: 'both schools')
                .addToSchools('MIT').addToSchools('Harvard').save(failOnError: true)
        new CreateAliasStudent(name: 'MIT only').addToSchools('MIT').save(failOnError: true)
        new CreateAliasStudent(name: 'Harvard only').addToSchools('Harvard').save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasStudent.withCriteria {
            if (explicitInner) {
                createAlias('schools', 'm', 0)
            } else {
                createAlias('schools', 'm')
            }
            createAlias('schools', 'h')
            inList('m', ['MIT'])
            inList('h', ['Harvard'])
        }

        then:
        rows*.id == [student.id]
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_student_schools\b/).size() == 2

        where:
        explicitInner << [false, true]
    }

    void 'unused basic collection aliases preserve rows and counts with join type #joinType and membership #restrictMembership'() {
        given:
        new CreateAliasStudent(name: 'grace').addToSchools('Yale').addToSchools('Princeton').save(failOnError: true)
        new CreateAliasStudent(name: 'hank').addToSchools('Princeton').save(failOnError: true)
        new CreateAliasStudent(name: 'none').save(flush: true, failOnError: true)

        when:
        def names = CreateAliasStudent.withCriteria {
            createAlias('schools', 's', joinType)
            if (restrictMembership) {
                inList('schools', ['Princeton'])
            }
            projections { property('name') }
            order('name', 'asc')
        }

        then:
        names == expectedNames

        when:
        def count = CreateAliasStudent.createCriteria().count {
            createAlias('schools', 's', joinType)
            if (restrictMembership) {
                inList('schools', ['Princeton'])
            }
        }

        then:
        count == expectedNames.size()

        when:
        def rows = CreateAliasStudent.createCriteria().list(max: 1) {
            createAlias('schools', 's', joinType)
            if (restrictMembership) {
                inList('schools', ['Princeton'])
            }
            order('name', 'asc')
        }

        then:
        rows*.name == ['grace']
        rows.totalCount == expectedNames.size()

        where:
        joinType | restrictMembership | expectedNames
        0        | false              | ['grace', 'hank', 'none']
        1        | false              | ['grace', 'hank', 'none']
        0        | true               | ['grace', 'hank']
        1        | true               | ['grace', 'hank']
    }

    void 'basic collection membership remains independent of an element alias'() {
        given:
        new CreateAliasStudent(name: 'both schools')
                .addToSchools('MIT').addToSchools('Harvard').save(failOnError: true)
        new CreateAliasStudent(name: 'MIT only').addToSchools('MIT').save(flush: true, failOnError: true)

        expect:
        CreateAliasStudent.withCriteria {
            createAlias('schools', 's')
            eq('s', 'MIT')
            inList('schools', ['Harvard'])
            projections { property('name') }
            order('s', 'asc')
        } == ['both schools']
    }

    void 'basic collection aliases preserve their own join types: #firstType and #secondType'() {
        given:
        new CreateAliasStudent(name: 'student').addToSchools('MIT').save(flush: true, failOnError: true)

        when:
        def rows = CreateAliasStudent.withCriteria {
            createAlias('schools', 'm', firstType)
            createAlias('schools', 'h', secondType)
            projections {
                property('m')
                property('h')
            }
            order('m', 'asc')
            order('h', 'asc')
        }

        then:
        rows.size() == 1
        rows[0].toList() == ['MIT', 'MIT']
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_student_schools\b/).size() == 2
        sqlCapture.statements.last().findAll(/(?i)\bleft join create_alias_student_schools\b/).size() == 1

        where:
        firstType | secondType
        0         | 1
        1         | 0
    }

    void 'basic collection size remains available with an element alias resolved first: #aliasFirst'() {
        given:
        new CreateAliasStudent(name: 'both schools')
                .addToSchools('MIT').addToSchools('Harvard').save(failOnError: true)
        new CreateAliasStudent(name: 'MIT only').addToSchools('MIT').save(flush: true, failOnError: true)

        when:
        def names = CreateAliasStudent.withCriteria {
            createAlias('schools', 's')
            if (aliasFirst) {
                eq('s', 'MIT')
                sizeEq('schools', 2)
            } else {
                sizeEq('schools', 2)
                eq('s', 'MIT')
            }
            projections { property('name') }
            if (aliasFirst) {
                order('s', 'asc')
            }
        }

        then:
        names == ['both schools']

        where:
        aliasFirst << [false, true]
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

    void 'subquery aliases join their own root when the outer query joins the same association'() {
        given:
        def x = new CreateAliasLike(label: 'x').save(failOnError: true)
        def y = new CreateAliasLike(label: 'y').save(failOnError: true)
        new CreateAliasTeam(name: 'x', favorite: y).save(failOnError: true)
        new CreateAliasTeam(name: 'y', favorite: y).save(failOnError: true)
        new CreateAliasTeam(name: 'z', favorite: x).save(flush: true, failOnError: true)
        def namesWithFavoriteY = new DetachedCriteria(CreateAliasTeam).build {
            createAlias('favorite', 'g')
            eq('g.label', 'y')
            projections { property('name') }
        }

        when:
        def names = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            inList('f.label', namesWithFavoriteY)
            projections { property('name') }
            order('name', 'asc')
        }

        then:
        names == ['x', 'y', 'z']
    }

    void 'subquery association blocks join their own root when the outer query uses the same association path'() {
        given:
        def x = new CreateAliasLike(label: 'x').save(failOnError: true)
        def y = new CreateAliasLike(label: 'y').save(failOnError: true)
        new CreateAliasTeam(name: 'x', favorite: y).save(failOnError: true)
        new CreateAliasTeam(name: 'y', favorite: y).save(failOnError: true)
        new CreateAliasTeam(name: 'z', favorite: x).save(flush: true, failOnError: true)
        def namesWithFavoriteY = new DetachedCriteria(CreateAliasTeam).build {
            favorite {
                eq('label', 'y')
            }
            projections { property('name') }
        }

        when:
        def names = CreateAliasTeam.withCriteria {
            inList('favorite.label', namesWithFavoriteY)
            projections { property('name') }
            order('name', 'asc')
        }

        then:
        names == ['x', 'y', 'z']
    }

    void 'exists subquery alias #innerAlias joins its own root for the same association path'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def teamA = new CreateAliasTeam(name: 'A', favorite: first).save(failOnError: true)
        new CreateAliasTeam(name: 'B', favorite: second).save(flush: true, failOnError: true)
        def teamsWithSecondFavorite = new DetachedCriteria(CreateAliasTeam).build {
            createAlias('favorite', innerAlias)
            eq("${innerAlias}.label", 'second')
        }

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'outside')
            eq('outside.label', 'first')
            exists(teamsWithSecondFavorite)
        }

        then:
        rows*.id == [teamA.id]

        where:
        innerAlias << ['inside', 'favorite', 'outside']
    }

    void 'an implicit subquery path resolves against its own root with nested junction #nestedJunction'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def teamA = new CreateAliasTeam(name: 'A', favorite: first).save(failOnError: true)
        new CreateAliasTeam(name: 'B', favorite: second).save(flush: true, failOnError: true)
        def teamsWithSecondFavorite = new DetachedCriteria(CreateAliasTeam).build {
            if (nestedJunction) {
                and {
                    eq('favorite.label', 'second')
                }
            } else {
                eq('favorite.label', 'second')
            }
        }

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'outside')
            eq('outside.label', 'first')
            exists(teamsWithSecondFavorite)
        }

        then:
        rows*.id == [teamA.id]

        where:
        nestedJunction << [false, true]
    }

    void 'subquery property comparisons use local paths with reversed operands #reverseOperands nested junction #nestedJunction and outer alias #outerAlias'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def teamA = new CreateAliasTeam(name: 'A', favorite: first).save(failOnError: true)
        new CreateAliasTeam(name: 'second', favorite: second).save(flush: true, failOnError: true)
        String leftProperty = reverseOperands ? 'favorite.label' : 'name'
        String rightProperty = reverseOperands ? 'name' : 'favorite.label'
        def ownNameIsOwnFavorite = new DetachedCriteria(CreateAliasTeam).build {
            if (nestedJunction) {
                and {
                    or {
                        eqProperty(leftProperty, rightProperty)
                    }
                }
            } else {
                eqProperty(leftProperty, rightProperty)
            }
        }

        when:
        def rows = CreateAliasTeam.withCriteria {
            if (outerAlias) {
                createAlias('favorite', 'outside')
                eq('outside.label', 'first')
            } else {
                eq('favorite.label', 'first')
            }
            exists(ownNameIsOwnFavorite)
        }

        then:
        rows*.id == [teamA.id]

        where:
        reverseOperands | nestedJunction | outerAlias
        false           | false          | true
        false           | true           | true
        true            | false          | true
        true            | true           | true
        false           | false          | false
        false           | true           | false
        true            | false          | false
        true            | true           | false
    }

    void 'a subquery without the association correlates through the outer path with explicit alias #explicitAlias and reversed operands #reverseOperands'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        new CreateAliasTeam(name: 'A', favorite: first).save(failOnError: true)
        new CreateAliasTeam(name: 'B', favorite: second).save(flush: true, failOnError: true)
        def matchingFavorite = new DetachedCriteria(CreateAliasLike).build {
            eq('label', 'second')
            if (reverseOperands) {
                eqProperty('label', 'favorite.label')
            } else {
                eqProperty('favorite.label', 'label')
            }
        }.id()

        when:
        def names = CreateAliasTeam.withCriteria {
            if (explicitAlias) {
                createAlias('favorite', 'f')
                isNotNull('f.label')
            } else {
                isNotNull('favorite.label')
            }
            exists(matchingFavorite)
            projections { property('name') }
        }

        then:
        names == ['B']
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 1

        where:
        explicitAlias | reverseOperands
        false         | false
        false         | true
        true          | false
        true          | true
    }

    void 'detached subqueries correlate through an enclosing association block with reversed operands #reverseOperands nested junction #nestedJunction and query #queryType'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        new CreateAliasTeam(name: 'A', favorite: first).save(failOnError: true)
        new CreateAliasTeam(name: 'B', favorite: second).save(flush: true, failOnError: true)
        String leftProperty = reverseOperands ? 'label' : 'favorite.label'
        String rightProperty = reverseOperands ? 'favorite.label' : 'label'
        def matchingFavorite = new DetachedCriteria(CreateAliasLike).build {
            eq('label', 'second')
            if (nestedJunction) {
                and {
                    or {
                        eqProperty(leftProperty, rightProperty)
                    }
                }
            } else {
                eqProperty(leftProperty, rightProperty)
            }
        }.id()
        def criteria = new DetachedCriteria(CreateAliasTeam).build {
            favorite { isNotNull('label') }
            exists(matchingFavorite)
        }

        when:
        def result
        if (queryType == 'count') {
            result = criteria.count()
        } else if (queryType == 'paginated') {
            result = criteria.list(max: 1, sort: 'name')
        } else {
            result = criteria.list()
        }

        then:
        if (queryType == 'count') {
            assert result == 1
        } else {
            assert result*.name == ['B']
            if (queryType == 'paginated') {
                assert result.totalCount == 1
            }
        }
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 1

        where:
        [reverseOperands, nestedJunction, queryType] << [[false, true], [false, true], ['list', 'count', 'paginated']].combinations()
    }

    void 'exists subqueries can deliberately reference an outer association alias with reversed operands #reverseOperands'() {
        given:
        def first = new CreateAliasLike(label: 'first').save(failOnError: true)
        def second = new CreateAliasLike(label: 'second').save(failOnError: true)
        def teamA = new CreateAliasTeam(name: 'A', favorite: first).save(failOnError: true)
        new CreateAliasTeam(name: 'B', favorite: second).save(flush: true, failOnError: true)
        def matchingFavorite = new DetachedCriteria(CreateAliasLike).build {
            eq('label', 'first')
            if (reverseOperands) {
                eqProperty('outside.label', 'label')
            } else {
                eqProperty('label', 'outside.label')
            }
        }

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'outside')
            isNotNull('outside.label')
            exists(matchingFavorite)
        }

        then:
        rows*.id == [teamA.id]

        where:
        reverseOperands << [false, true]
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

    void 'subqueries can explicitly reference an outer association alias'() {
        given:
        def x = new CreateAliasLike(label: 'x').save(failOnError: true)
        def y = new CreateAliasLike(label: 'y').save(failOnError: true)
        new CreateAliasTeam(name: 'x', favorite: y).save(failOnError: true)
        new CreateAliasTeam(name: 'y', favorite: y).save(failOnError: true)
        new CreateAliasTeam(name: 'z', favorite: x).save(flush: true, failOnError: true)
        def namesMatchingOuterFavorite = new DetachedCriteria(CreateAliasTeam).build {
            eqProperty('name', 'f.label')
            projections { property('name') }
        }

        expect:
        CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            isNotNull('f.label')
            inList('name', namesMatchingOuterFavorite)
            projections { property('name') }
        } == ['y']
    }

    void 'nested alias path #declarations joins each association once'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'matching', favorite: likeA, tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'wrong favorite tag', favorite: likeB, tag: a).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite', tag: a).save(flush: true, failOnError: true)

        when:
        def names = CreateAliasTeam.withCriteria {
            for (declaration in declarations) {
                createAlias(declaration[0], declaration[1])
            }
            eq('favorite.label', 'a')
            eq('t.name', 'a')
            projections { property('name') }
        }

        then:
        names == ['matching']
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_tag\b/).size() == 1

        where:
        declarations << [
                [['favorite.tag', 't']],
                [['favorite', 'f'], ['favorite.tag', 't']],
                [['favorite.tag', 't'], ['favorite', 'f']]
        ]
    }

    void 'nested alias path #declarations keeps the join type of each alias'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: tag).save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        new CreateAliasTeam(name: 'tagged', favorite: tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'untagged', favorite: untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite').save(flush: true, failOnError: true)

        expect:
        CreateAliasTeam.withCriteria {
            for (declaration in declarations) {
                createAlias(declaration[0], declaration[1], declaration[2])
            }
            projections { property('name') }
            order('name', 'asc')
        } == expected

        where:
        declarations                                     || expected
        [['favorite.tag', 't', 0]]                       || ['tagged']
        [['favorite.tag', 't', 1]]                       || ['no favorite', 'tagged', 'untagged']
        [['favorite', 'f', 0], ['favorite.tag', 't', 1]] || ['tagged', 'untagged']
        [['favorite', 'f', 1], ['favorite.tag', 't', 0]] || ['tagged']
    }

    void 'nested alias path does not change the join type of a restricted first association'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: tag).save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        new CreateAliasTeam(name: 'tagged', favorite: tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'untagged', favorite: untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite').save(flush: true, failOnError: true)

        expect:
        CreateAliasTeam.withCriteria {
            createAlias('favorite.tag', 't', 1)
            or {
                eq('favorite.label', 'tagged')
                eq('name', 'no favorite')
            }
            projections { property('name') }
            order('name', 'asc')
        } == CreateAliasTeam.withCriteria {
            or {
                eq('favorite.label', 'tagged')
                eq('name', 'no favorite')
            }
            projections { property('name') }
            order('name', 'asc')
        }
    }

    void 'detached nested alias path uses the configured join types'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: tag).save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        new CreateAliasTeam(name: 'tagged', favorite: tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'untagged', favorite: untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite').save(flush: true, failOnError: true)
        def criteria = new DetachedCriteria(CreateAliasTeam).build {
            join('favorite', JoinType.INNER)
            join('favorite.tag', JoinType.LEFT)
            createAlias('favorite.tag', 't')
        }

        expect:
        criteria.list()*.name.sort() == ['tagged', 'untagged']
    }

    void 'nested alias path #innerAlias in a subquery joins from its own root'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        def teamA = new CreateAliasTeam(name: 'A', favorite: likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'B', favorite: likeB).save(flush: true, failOnError: true)
        def teamsWithFavoriteTagB = new DetachedCriteria(CreateAliasTeam).build {
            createAlias('favorite.tag', innerAlias)
            eq("${innerAlias}.name", 'b')
        }

        when:
        def rows = CreateAliasTeam.withCriteria {
            createAlias('favorite.tag', 'outside')
            eq('favorite.label', 'a')
            eq('outside.name', 'a')
            exists(teamsWithFavoriteTagB)
        }

        then:
        rows*.id == [teamA.id]

        where:
        innerAlias << ['inside', 'outside']
    }

    void 'nested alias path #declarations shares the join of an alias on the associations before its last'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        def team = new CreateAliasTeam(name: 'team').addToLikes(likeA).addToLikes(likeB).save(failOnError: true)
        new CreateAliasClub(name: 'club', team: team).save(flush: true, failOnError: true)

        expect: 'the tag is the tag of the like the other alias restricts'
        CreateAliasClub.withCriteria {
            for (declaration in declarations) {
                createAlias(declaration[0], declaration[1])
            }
            eq('l.label', 'a')
            eq('t.name', tagName)
            projections { property('name') }
        } == expected

        where:
        declarations                                                   | tagName || expected
        [['team.likes', 'l'], ['team.likes.tag', 't']]                 | 'a'     || ['club']
        [['team.likes', 'l'], ['team.likes.tag', 't']]                 | 'b'     || []
        [['team.likes.tag', 't'], ['team.likes', 'l']]                 | 'b'     || []
        [['team', 'tm'], ['team.likes', 'l'], ['team.likes.tag', 't']] | 'b'     || []
    }

    void 'nested alias paths through the same to-many association return one row for each of its elements'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        def team = new CreateAliasTeam(name: 'team').addToLikes(likeA).addToLikes(likeB).save(failOnError: true)
        new CreateAliasClub(name: 'club', team: team).save(flush: true, failOnError: true)

        expect:
        CreateAliasClub.withCriteria {
            createAlias('team.likes', 'l', 1)
            createAlias('team.likes.tag', 't', 1)
            projections {
                property('l.label')
                property('t.name')
            }
        }.collect { it as List }.sort() == [['a', 'a'], ['b', 'b']]
    }

    void 'nested alias path joins a to-one association before its last once'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def favorite = new CreateAliasLike(label: 'favorite', tag: tag).save(failOnError: true)
        def team = new CreateAliasTeam(name: 'team', favorite: favorite).save(failOnError: true)
        new CreateAliasClub(name: 'club', team: team).save(flush: true, failOnError: true)

        when:
        def names = CreateAliasClub.withCriteria {
            createAlias('team.favorite', 'f')
            createAlias('team.favorite.tag', 't')
            eq('f.label', 'favorite')
            eq('t.name', 'tag')
            projections { property('name') }
        }

        then:
        names == ['club']
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_team\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_tag\b/).size() == 1
    }

    void 'nested alias path declared #position an association block keeps the inner join of the block'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: tag).save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        new CreateAliasTeam(name: 'tagged', favorite: tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'untagged', favorite: untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite').save(flush: true, failOnError: true)

        expect: 'a team with no favorite does not satisfy a restriction on its favorite'
        CreateAliasTeam.withCriteria(criteria)*.name == ['untagged']
        new DetachedCriteria(CreateAliasTeam).build(detachedCriteria).list()*.name == ['untagged']

        where:
        position << ['before', 'after']
        criteria << [
                { createAlias('favorite.tag', 't', 1); favorite { isNull('tag') } },
                { favorite { isNull('tag') }; createAlias('favorite.tag', 't', 1) }
        ]
        detachedCriteria << [
                { createAlias('favorite.tag', 't'); join('favorite.tag', JoinType.LEFT); favorite { isNull('tag') } },
                { favorite { isNull('tag') }; createAlias('favorite.tag', 't'); join('favorite.tag', JoinType.LEFT) }
        ]
    }

    void 'detached criteria joins a nested path that has a join type and no alias one association at a time'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: tag).save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        new CreateAliasTeam(name: 'tagged', favorite: tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'untagged', favorite: untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite').save(flush: true, failOnError: true)

        expect:
        new DetachedCriteria(CreateAliasTeam).build {
            join('favorite.tag', JoinType.LEFT)
            isNull('favorite.tag')
        }.list()*.name == ['untagged']
    }

    void 'an alias path that starts with an earlier alias continues from the join of that alias'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'favorite a', favorite: likeA).save(failOnError: true)
        new CreateAliasTeam(name: 'favorite b', favorite: likeB).save(flush: true, failOnError: true)

        when:
        def names = CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            createAlias('f.tag', 't')
            eq('f.label', 'a')
            eq('t.name', 'a')
            projections { property('name') }
        }

        then:
        names == ['favorite a']
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_like\b/).size() == 1
        sqlCapture.statements.last().findAll(/(?i)\bjoin create_alias_tag\b/).size() == 1
    }

    void 'an alias path that starts with an earlier alias keeps the join type of each alias #declarations'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: tag).save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        new CreateAliasTeam(name: 'tagged', favorite: tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'untagged', favorite: untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite').save(flush: true, failOnError: true)

        expect:
        CreateAliasTeam.withCriteria {
            for (declaration in declarations) {
                createAlias(declaration[0], declaration[1], declaration[2])
            }
            projections { property('name') }
            order('name', 'asc')
        } == expected

        where:
        declarations                               || expected
        [['favorite', 'f', 0], ['f.tag', 't', 0]]  || ['tagged']
        [['favorite', 'f', 0], ['f.tag', 't', 1]]  || ['tagged', 'untagged']
        [['favorite', 'f', 1], ['f.tag', 't', 1]]  || ['no favorite', 'tagged', 'untagged']
        [['favorite', 'f', 1], ['f.tag', 't', 0]]  || ['tagged']
    }

    void 'alias paths #declarations that start with an alias share the joins of the same association path'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        def team = new CreateAliasTeam(name: 'team').addToLikes(likeA).addToLikes(likeB).save(failOnError: true)
        new CreateAliasClub(name: 'club', team: team).save(flush: true, failOnError: true)

        expect: 'the tag is the tag of the like the other alias restricts'
        CreateAliasClub.withCriteria {
            for (declaration in declarations) {
                createAlias(declaration[0], declaration[1])
            }
            eq('l.label', 'a')
            eq('t.name', tagName)
            projections { property('name') }
        } == expected

        where:
        declarations                                                 | tagName || expected
        [['team', 'tm'], ['tm.likes', 'l'], ['l.tag', 't']]          | 'a'     || ['club']
        [['team', 'tm'], ['tm.likes', 'l'], ['l.tag', 't']]          | 'b'     || []
        [['team', 'tm'], ['tm.likes', 'l'], ['tm.likes.tag', 't']]   | 'b'     || []
        [['team', 'tm'], ['tm.likes', 'l'], ['team.likes.tag', 't']] | 'b'     || []
        [['team.likes', 'l'], ['l.tag', 't']]                        | 'b'     || []
    }

    void 'an alias path that starts with a later alias of an association continues from the join of that alias'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'team').addToLikes(likeA).addToLikes(likeB).save(flush: true, failOnError: true)

        expect: 'each tag is the tag of the like its own alias refers to'
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'first')
            createAlias('likes', 'second')
            createAlias('first.tag', 'firstTag')
            createAlias('second.tag', 'secondTag')
            eq('first.label', 'a')
            eq('second.label', 'b')
            eq('firstTag.name', 'a')
            eq('secondTag.name', secondTagName)
            projections { property('name') }
        } == expected

        where:
        secondTagName || expected
        'b'           || ['team']
        'a'           || []
    }

    void 'the first segment of an alias path refers to an alias before an association with the same name'() {
        given:
        def a = new CreateAliasTag(name: 'a').save(failOnError: true)
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: a).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: b).save(failOnError: true)
        new CreateAliasTeam(name: 'team', favorite: likeB).addToLikes(likeA).save(flush: true, failOnError: true)

        expect: 'favorite.tag is the tag of a like, as favorite is an alias of likes'
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'favorite')
            createAlias('favorite.tag', 't')
            eq('t.name', tagName)
            projections { property('name') }
        } == expected

        where:
        tagName || expected
        'a'     || ['team']
        'b'     || []
    }

    void 'an alias path that starts with an alias is relative to the association block that declares it'() {
        given:
        def tag = new CreateAliasTag(name: 'tag').save(failOnError: true)
        def favorite = new CreateAliasLike(label: 'favorite', tag: tag).save(failOnError: true)
        def withFavorite = new CreateAliasTeam(name: 'with favorite', favorite: favorite).save(failOnError: true)
        def withoutFavorite = new CreateAliasTeam(name: 'without favorite').save(failOnError: true)
        new CreateAliasClub(name: 'club', team: withFavorite).save(failOnError: true)
        new CreateAliasClub(name: 'other', team: withoutFavorite).save(flush: true, failOnError: true)

        when:
        def clubs = CreateAliasClub.createCriteria().list(max: 1) {
            team {
                createAlias('favorite', 'f')
                createAlias('f.tag', 't')
                eq('t.name', 'tag')
            }
        }

        then:
        clubs*.name == ['club']
        clubs.totalCount == 1
    }

    void 'an alias path #path that starts with an alias is rejected when it is not a chain of associations'() {
        when:
        CreateAliasTeam.withCriteria {
            createAlias('favorite', 'f')
            createAlias(path, 'x')
        }

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Argument [${path}] is not an association"

        where:
        path << ['f.label', 'f.missing', 'f.tag.name', 'f.label.tag']
    }

    void 'a property of an association block is that property, not an outer alias or join of the same name'() {
        given: 'a team whose like has no tag, one whose like has a tag, and one with no like'
        def b = new CreateAliasTag(name: 'b').save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: b).save(failOnError: true)
        def matching = new CreateAliasTeam(name: 'matching', tag: b).addToLikes(untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'tagged like', tag: b).addToLikes(tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no likes', tag: b).save(flush: true, failOnError: true)

        expect: 'the tag of the team never makes isNull(\'tag\') false for the like'
        CreateAliasTeam.withCriteria { createAlias('tag', 'tag'); likes { isNull('tag') } }*.id == [matching.id]
        CreateAliasTeam.withCriteria { tag { eq('name', 'b') }; likes { isNull('tag') } }*.id == [matching.id]
        CreateAliasTeam.withCriteria { likes { isNotNull('tag') } }*.name == ['tagged like']
        new DetachedCriteria(CreateAliasTeam).build { createAlias('tag', 't'); likes { isNull('tag') } }.list()*.id == [matching.id]
        new DetachedCriteria(CreateAliasTeam).build { createAlias('tag', 'tag'); likes { isNull('tag') } }.list()*.id == [matching.id]
    }

    void 'a left join requested in an association block is kept when an outer alias has the same name'() {
        given: 'a team whose favorite has no tag, one whose favorite has a tag, and one with no favorite'
        def x = new CreateAliasTag(name: 'x').save(failOnError: true)
        def untagged = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        def tagged = new CreateAliasLike(label: 'tagged', tag: x).save(failOnError: true)
        def matching = new CreateAliasTeam(name: 'matching', tag: x, favorite: untagged).save(failOnError: true)
        new CreateAliasTeam(name: 'tagged favorite', tag: x, favorite: tagged).save(failOnError: true)
        new CreateAliasTeam(name: 'no favorite', tag: x).save(flush: true, failOnError: true)

        expect: 'the favorite is joined, and its missing tag is a null name'
        CreateAliasTeam.withCriteria { createAlias('tag', 'tag'); favorite { tag(1) { isNull('name') } } }*.id == [matching.id]
        CreateAliasTeam.withCriteria { favorite { tag(1) { isNull('name') } } }*.id == [matching.id]
    }

    void 'a nested alias path and a block on the same association restrict the same element'() {
        given: 'a team with the likes (a, tag A) and (b, tag B), and one with the like (a, tag B)'
        def tagA = new CreateAliasTag(name: 'tagA').save(failOnError: true)
        def tagB = new CreateAliasTag(name: 'tagB').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: tagA).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: tagB).save(failOnError: true)
        def likeAB = new CreateAliasLike(label: 'a', tag: tagB).save(failOnError: true)
        new CreateAliasTeam(name: 'split').addToLikes(likeA).addToLikes(likeB).save(failOnError: true)
        def together = new CreateAliasTeam(name: 'together').addToLikes(likeAB).save(flush: true, failOnError: true)

        and: 'a club of each team'
        new CreateAliasClub(name: 'split club', team: CreateAliasTeam.findByName('split')).save(failOnError: true)
        def club = new CreateAliasClub(name: 'together club', team: together).save(flush: true, failOnError: true)

        expect: 'one like has to be both label a and tag B'
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't'); team { likes { eq('label', 'a') } }; eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasClub.withCriteria { team { likes { eq('label', 'a') } }; createAlias('team.likes.tag', 't'); eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't'); eq('t.name', 'tagB'); team { likes { eq('label', 'a') } } }*.id == [club.id]
        CreateAliasTeam.withCriteria { createAlias('likes.tag', 't'); likes { eq('label', 'a') }; eq('t.name', 'tagB') }*.id == [together.id]
        CreateAliasTeam.withCriteria { likes { eq('label', 'a') }; createAlias('likes.tag', 't'); eq('t.name', 'tagB') }*.id == [together.id]
        new DetachedCriteria(CreateAliasTeam).build { createAlias('likes.tag', 't'); likes { eq('label', 'a') }; eq('t.name', 'tagB') }.list()*.id == [together.id]
    }

    void 'a nested alias path and a block that declares its own alias on the same association restrict the same element'() {
        given: 'a team with the likes (a, tag A) and (b, tag B), and one with the like (a, tag B)'
        def tagA = new CreateAliasTag(name: 'tagA').save(failOnError: true)
        def tagB = new CreateAliasTag(name: 'tagB').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: tagA).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: tagB).save(failOnError: true)
        def likeAB = new CreateAliasLike(label: 'a', tag: tagB).save(failOnError: true)
        new CreateAliasTeam(name: 'split').addToLikes(likeA).addToLikes(likeB).save(failOnError: true)
        def together = new CreateAliasTeam(name: 'together').addToLikes(likeAB).save(flush: true, failOnError: true)
        new CreateAliasClub(name: 'split club', team: CreateAliasTeam.findByName('split')).save(failOnError: true)
        def club = new CreateAliasClub(name: 'together club', team: together).save(flush: true, failOnError: true)

        expect: 'one like has to be both label a and tag B, whichever alias is declared first'
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't'); team { createAlias('likes', 'l'); eq('l.label', 'a') }; eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasClub.withCriteria { team { createAlias('likes', 'l'); eq('l.label', 'a') }; createAlias('team.likes.tag', 't'); eq('t.name', 'tagB') }*.id == [club.id]
        new DetachedCriteria(CreateAliasClub).build { createAlias('team.likes.tag', 't'); team { createAlias('likes', 'l'); eq('l.label', 'a') }; eq('t.name', 'tagB') }.list()*.id == [club.id]
    }

    void 'a block that asks for an inner join keeps it when a nested alias path joins the same association with a left join'() {
        given: 'a club whose team has no likes, and one whose team has a like without a tag'
        def bare = new CreateAliasTeam(name: 'bare').save(failOnError: true)
        def like = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        def liked = new CreateAliasTeam(name: 'liked').addToLikes(like).save(flush: true, failOnError: true)
        new CreateAliasClub(name: 'bare club', team: bare).save(failOnError: true)
        def likedClub = new CreateAliasClub(name: 'liked club', team: liked).save(flush: true, failOnError: true)

        expect: 'the block needs a like, so only the club whose team has one is returned'
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't', 1); team { likes { isNull('tag') } } }*.id == [likedClub.id]
        CreateAliasClub.withCriteria { team { likes { isNull('tag') } }; createAlias('team.likes.tag', 't', 1) }*.id == [likedClub.id]
        new DetachedCriteria(CreateAliasClub).build { createAlias('team.likes.tag', 't'); join('team.likes.tag', JoinType.LEFT); team { likes { isNull('tag') } } }.list()*.id == [likedClub.id]
    }


    void 'a block inside a junction keeps its inner join when a nested alias path joins the same association with a left join'() {
        given: 'a club whose team has no likes, and one whose team has a like without a tag'
        def bare = new CreateAliasTeam(name: 'bare').save(failOnError: true)
        def like = new CreateAliasLike(label: 'untagged').save(failOnError: true)
        def liked = new CreateAliasTeam(name: 'liked').addToLikes(like).save(flush: true, failOnError: true)
        new CreateAliasClub(name: 'bare club', team: bare).save(failOnError: true)
        def likedClub = new CreateAliasClub(name: 'liked club', team: liked).save(flush: true, failOnError: true)

        expect: 'the block needs a like, so only the club whose team has one is returned'
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't', 1); team { and { likes { isNull('tag') } } } }*.id == [likedClub.id]
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't', 1); team { or { likes { isNull('tag') } } } }*.id == [likedClub.id]
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't', 1); and { team { likes { isNull('tag') } } } }*.id == [likedClub.id]
        CreateAliasClub.withCriteria { team { and { likes { isNull('tag') } } }; createAlias('team.likes.tag', 't', 1) }*.id == [likedClub.id]
    }

    void 'an implicit association path and a nested alias path restrict the same element'() {
        given: 'a team with the likes (a, tag A) and (b, tag B), and one with the like (a, tag B)'
        def tagA = new CreateAliasTag(name: 'tagA').save(failOnError: true)
        def tagB = new CreateAliasTag(name: 'tagB').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: tagA).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: tagB).save(failOnError: true)
        def likeAB = new CreateAliasLike(label: 'a', tag: tagB).save(failOnError: true)
        new CreateAliasTeam(name: 'split').addToLikes(likeA).addToLikes(likeB).save(failOnError: true)
        def together = new CreateAliasTeam(name: 'together').addToLikes(likeAB).save(flush: true, failOnError: true)
        new CreateAliasClub(name: 'split club', team: CreateAliasTeam.findByName('split')).save(failOnError: true)
        def club = new CreateAliasClub(name: 'together club', team: together).save(flush: true, failOnError: true)

        expect: 'one like has to be both label a and tag B'
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't'); eq('team.likes.label', 'a'); eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasClub.withCriteria { eq('team.likes.label', 'a'); createAlias('team.likes.tag', 't'); eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't'); team { eq('likes.label', 'a') }; eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't'); team { and { likes { eq('label', 'a') } } }; eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasClub.withCriteria { createAlias('team.likes.tag', 't'); and { team { likes { eq('label', 'a') } } }; eq('t.name', 'tagB') }*.id == [club.id]
        CreateAliasTeam.withCriteria { createAlias('likes.tag', 't'); eq('likes.label', 'a'); eq('t.name', 'tagB') }*.id == [together.id]
    }
    void 'a block that asks for a left join keeps it when an alias of the same name as its association is declared outside it'() {
        given: 'a team with a favorite and no likes, one with a favorite and a like, and one with neither'
        def favorite = new CreateAliasLike(label: 'f').save(failOnError: true)
        def likeX = new CreateAliasLike(label: 'x').save(failOnError: true)
        def noLikes = new CreateAliasTeam(name: 'no likes', favorite: favorite).save(failOnError: true)
        new CreateAliasTeam(name: 'with like', favorite: favorite).addToLikes(likeX).save(failOnError: true)
        new CreateAliasTeam(name: 'bare').save(flush: true, failOnError: true)

        expect: 'the unreferenced alias still joins the favorite, and the left join of the block returns the team without likes'
        CreateAliasTeam.withCriteria { createAlias('favorite', 'likes'); likes(1) { isNull('label') } }*.id == [noLikes.id]
        CreateAliasTeam.withCriteria { likes(1) { isNull('label') }; createAlias('favorite', 'likes') }*.id == [noLikes.id]
    }

    void 'a dotted restriction on a second alias of the same association keeps the join of that alias'() {
        given: 'a team whose two likes split the two tags'
        def tagA = new CreateAliasTag(name: 'a').save(failOnError: true)
        def tagB = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: tagA).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: tagB).save(failOnError: true)
        def team = new CreateAliasTeam(name: 'split').addToLikes(likeA).addToLikes(likeB).save(flush: true, failOnError: true)
        def other = new CreateAliasTeam(name: 'only a').addToLikes(new CreateAliasLike(label: 'a2', tag: tagA).save(failOnError: true))
                .save(flush: true, failOnError: true)

        expect: 'each alias restricts its own like, so only the team with a like of each tag matches'
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'first')
            createAlias('likes', 'second')
            eq('first.tag.name', 'a')
            eq('second.tag.name', 'b')
            projections { id() }
        } == [team.id]

        and: 'the other way round matches the same team'
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'first')
            createAlias('likes', 'second')
            eq('first.tag.name', 'b')
            eq('second.tag.name', 'a')
            projections { id() }
        } == [team.id]

        and: 'one alias alone still restricts only its own join (two independent joins repeat a team, so the ids are compared as a set)'
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'first')
            createAlias('likes', 'second')
            eq('second.tag.name', 'a')
            projections { id() }
        } as Set == [team.id, other.id] as Set
    }

    void 'a subquery compares with a dotted property of the second outer alias, not with its own path'() {
        given:
        def tagA = new CreateAliasTag(name: 'a').save(failOnError: true)
        def tagB = new CreateAliasTag(name: 'b').save(failOnError: true)
        def likeA = new CreateAliasLike(label: 'a', tag: tagA).save(failOnError: true)
        def likeB = new CreateAliasLike(label: 'b', tag: tagB).save(failOnError: true)
        def team = new CreateAliasTeam(name: 'split').addToLikes(likeA).addToLikes(likeB).save(flush: true, failOnError: true)
        def sameTagAs = { String label ->
            new DetachedCriteria(CreateAliasLike).build {
                eq('label', label)
                eqProperty('tag.name', 'second.tag.name')
            }
        }

        expect: 'the second alias is the like labelled b, whose tag is b: a like labelled a has another tag'
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'first')
            createAlias('likes', 'second')
            eq('second.label', 'b')
            exists(sameTagAs('a'))
            projections { id() }
        }.isEmpty()

        and: 'a like labelled b has the tag of the second alias'
        CreateAliasTeam.withCriteria {
            createAlias('likes', 'first')
            createAlias('likes', 'second')
            eq('second.label', 'b')
            exists(sameTagAs('b'))
            projections { id() }
        } as Set == [team.id] as Set
    }
}

@Entity
class CreateAliasTeam {
    String name
    CreateAliasLike favorite
    CreateAliasTag tag

    static hasMany = [likes: CreateAliasLike]

    static constraints = {
        favorite nullable: true
        tag nullable: true
    }
}

@Entity
class CreateAliasLike {
    String label
    CreateAliasTag tag

    static constraints = {
        tag nullable: true
    }
}

@Entity
class CreateAliasTag {
    String name
}

@Entity
class CreateAliasClub {
    String name
    CreateAliasTeam team
}

@Entity
class CreateAliasOwner {
    String name

    static hasMany = [likes: CreateAliasLike]
}

@Entity
class CreateAliasStudent {
    String name

    static hasMany = [schools: String]
}

class CreateAliasSqlCapture implements StatementInspector {
    final List<String> statements = []

    @Override
    String inspect(String sql) {
        statements.add(sql)
        sql
    }
}