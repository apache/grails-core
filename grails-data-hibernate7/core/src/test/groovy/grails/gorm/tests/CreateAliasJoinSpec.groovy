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
        manager.registerDomainClasses(CreateAliasTeam, CreateAliasLike, CreateAliasStudent, CreateAliasTag, CreateAliasOwner)
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
        rows.totalCount == 2
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