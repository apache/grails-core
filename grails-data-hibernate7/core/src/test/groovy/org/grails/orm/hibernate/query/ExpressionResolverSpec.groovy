/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.query

import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Expression
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.SingularAttribute
import org.hibernate.metamodel.model.domain.PersistentAttribute
import org.hibernate.query.sqm.tree.domain.SqmPath
import org.hibernate.query.sqm.tree.from.SqmAttributeJoin
import org.hibernate.query.sqm.tree.from.SqmFrom
import org.hibernate.query.sqm.tree.from.SqmJoin
import spock.lang.Specification

/**
 * Unit tests for ExpressionResolver.
 */
class ExpressionResolverSpec extends Specification {

    def "test resolve simple property path"() {
        given:
        def root = Mock(From)
        def propPath = Mock(Path)
        def joinTracker = new JoinTracker(root)
        def aliasRegistry = new AliasRegistry()
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve("firstName")

        then:
        1 * root.get("firstName") >> propPath
        result == propPath
    }

    def "test resolve {alias} returns root"() {
        given:
        def root = Mock(From)
        def joinTracker = new JoinTracker(root)
        def resolver = new ExpressionResolver(new AliasRegistry(), joinTracker)

        expect:
        resolver.resolve("{alias}") == root
        resolver.resolve("root") == root
    }

    def "test resolve with alias:property separator"() {
        given:
        def root = Mock(From)
        def faceJoin = Mock(Join)
        def namePath = Mock(Path)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define("f", new HibernateAlias("face", "f", JoinType.INNER))
        def joinTracker = new JoinTracker(root)
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve("f:name")

        then:
        1 * root.join("face", JoinType.INNER) >> faceJoin
        1 * faceJoin.get("name") >> namePath
        result == namePath
        aliasRegistry.getRealized("f") == faceJoin
        resolver.resolve('f') == faceJoin
        joinTracker.getJoin("f") == null
        joinTracker.getJoin("face") == faceJoin
        resolver.resolve('face') == faceJoin
    }

    def "test resolve with dot notation for alias #alias"() {
        given:
        def root = Mock(From)
        def faceJoin = Mock(Join)
        def namePath = Mock(Path)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define(alias, new HibernateAlias("face", alias, JoinType.INNER))
        def joinTracker = new JoinTracker(root)
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve("${alias}.name")

        then:
        1 * root.join("face", JoinType.INNER) >> faceJoin
        1 * faceJoin.get("name") >> namePath
        result == namePath
        joinTracker.getJoin("face") == faceJoin
        resolver.resolve(alias) == faceJoin

        where:
        alias << ['f', 'face']
    }

    def "test resolve using already joined path"() {
        given:
        def root = Mock(From)
        def nicknamesJoin = Mock(Join)
        def joinTracker = new JoinTracker(root)
        joinTracker.addJoin("nicknames", nicknamesJoin)
        def resolver = new ExpressionResolver(new AliasRegistry(), joinTracker)

        expect:
        resolver.resolve("nicknames") == nicknamesJoin
    }

    def 'an alias definition takes precedence over a cached join with the same name'() {
        given:
        def root = Mock(From)
        def favoriteJoin = Mock(Join)
        def likesJoin = Mock(Join)
        def labelPath = Mock(Path)
        def joinTracker = new JoinTracker(root)
        joinTracker.addJoin('favorite', favoriteJoin)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define('favorite', new HibernateAlias('likes', 'favorite'))
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve('favorite.label')

        then:
        1 * root.join('likes', JoinType.INNER) >> likesJoin
        1 * likesJoin.get('label') >> labelPath
        0 * favoriteJoin.get('label')
        result == labelPath
        resolver.resolve('favorite') == likesJoin
        joinTracker.getJoin('favorite') == favoriteJoin
        joinTracker.getJoin('likes') == likesJoin
    }

    def 'nested alias paths resolved in the order #order share the join of each association before their last'() {
        given:
        def root = Mock(From)
        def teamJoin = Mock(Join)
        def likesJoin = Mock(Join)
        def tagJoin = Mock(Join)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define('l', new HibernateAlias('team.likes', 'l'))
        aliasRegistry.define('t', new HibernateAlias('team.likes.tag', 't'))
        def resolver = new ExpressionResolver(aliasRegistry, new JoinTracker(root))

        when:
        def resolved = order.collectEntries { [it, resolver.resolve(it)] }

        then:
        1 * root.join('team', JoinType.INNER) >> teamJoin
        1 * teamJoin.join('likes', JoinType.INNER) >> likesJoin
        1 * likesJoin.join('tag', JoinType.INNER) >> tagJoin
        resolved == [l: likesJoin, t: tagJoin]

        where:
        order << [['l', 't'], ['t', 'l']]
    }

    def 'an association before the last of a nested alias path is joined with #expected when its configured join type is #configured'() {
        given:
        def root = Mock(From)
        def favoriteJoin = Mock(Join)
        def tagJoin = Mock(Join)
        def aliasRegistry = new AliasRegistry()
        if (configured) {
            aliasRegistry.define('favorite', new HibernateAlias('favorite', 'favorite', configured, false))
        }
        aliasRegistry.define('t', new HibernateAlias('favorite.tag', 't', JoinType.LEFT))
        def resolver = new ExpressionResolver(aliasRegistry, new JoinTracker(root))

        when:
        def result = resolver.resolve('t')

        then:
        1 * root.join('favorite', expected) >> favoriteJoin
        1 * favoriteJoin.join('tag', JoinType.LEFT) >> tagJoin
        result == tagJoin

        where:
        configured     || expected
        null           || JoinType.LEFT
        JoinType.INNER || JoinType.INNER
        JoinType.RIGHT || JoinType.RIGHT
    }

    def 'a nested association path without an alias is joined one association at a time'() {
        given:
        def root = Mock(From)
        def favoriteJoin = Mock(Join)
        def tagJoin = Mock(Join)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define('favorite.tag', new HibernateAlias('favorite.tag', 'favorite.tag', JoinType.LEFT, false))
        def joinTracker = new JoinTracker(root)
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        when:
        def result = resolver.resolve('favorite.tag')

        then:
        1 * root.join('favorite', JoinType.LEFT) >> favoriteJoin
        1 * favoriteJoin.join('tag', JoinType.LEFT) >> tagJoin
        0 * root.join('favorite.tag', _)
        result == tagJoin
        joinTracker.getJoin('favorite') == favoriteJoin
        joinTracker.getJoin('favorite.tag') == tagJoin
    }

    def 'an alias path that names an alias before the separator is joined from the join of that alias'() {
        given:
        def root = Mock(From)
        def firstLikesJoin = Mock(Join)
        def secondLikesJoin = Mock(Join)
        def tagJoin = Mock(Join)
        def ownerJoin = Mock(Join)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define('first', new HibernateAlias('likes', 'first'))
        aliasRegistry.define('second', new HibernateAlias('likes', 'second'))
        aliasRegistry.define('t', new HibernateAlias('second:tag', 't', JoinType.LEFT))
        aliasRegistry.define('o', new HibernateAlias('second:tag.owner', 'o'))
        def resolver = new ExpressionResolver(aliasRegistry, new JoinTracker(root))

        when:
        def resolved = ['o', 't', 'second', 'first'].collectEntries { [it, resolver.resolve(it)] }

        then: 'the second alias of likes has a join of its own, which the paths continue from'
        2 * root.join('likes', JoinType.INNER) >>> [secondLikesJoin, firstLikesJoin]
        1 * secondLikesJoin.join('tag', JoinType.LEFT) >> tagJoin
        1 * tagJoin.join('owner', JoinType.INNER) >> ownerJoin
        0 * firstLikesJoin.join(*_)
        resolved == [o: ownerJoin, t: tagJoin, second: secondLikesJoin, first: firstLikesJoin]
    }

    def "test resolve with parent context using explicit alias #explicitAlias and realized expression #realized"() {
        given:
        // Parent context
        def parentRoot = Mock(From)
        def faceJoin = Mock(Join)
        def parentAliasRegistry = new AliasRegistry()
        def alias = explicitAlias ? 'f' : 'face'
        parentAliasRegistry.define(alias, new HibernateAlias('face', alias, JoinType.INNER, explicitAlias))
        if (realized) {
            parentAliasRegistry.realize(alias, faceJoin)
        }
        def parentJoinTracker = new JoinTracker(parentRoot)
        parentJoinTracker.addJoin('face', faceJoin)

        // Subquery context
        def subRoot = Mock(From)
        def aliasRegistry = new AliasRegistry(parentAliasRegistry)
        def joinTracker = new JoinTracker(parentJoinTracker, subRoot)
        def resolver = new ExpressionResolver(aliasRegistry, joinTracker)

        def namePath = Mock(Path)

        when:
        def result = resolver.resolve("${alias}.name")

        then:
        0 * subRoot.join(_, _) // Should NOT join on subquery root
        1 * faceJoin.get("name") >> namePath
        result == namePath

        where:
        explicitAlias | realized
        true          | true
        false         | true
        false         | false
    }

    private static final List<Attribute.PersistentAttributeType> ASSOCIATIONS = [
            Attribute.PersistentAttributeType.MANY_TO_ONE, Attribute.PersistentAttributeType.ONE_TO_ONE,
            Attribute.PersistentAttributeType.ONE_TO_MANY, Attribute.PersistentAttributeType.MANY_TO_MANY
    ]

    private SqmPath attributePath(Attribute.PersistentAttributeType type) {
        def attribute = Mock(SingularAttribute)
        attribute.getPersistentAttributeType() >> type
        attribute.isAssociation() >> (type in ASSOCIATIONS)
        def path = Mock(SqmPath)
        path.getModel() >> attribute
        path
    }

    private SqmAttributeJoin joinOf(String attributeName, boolean fetched = false) {
        def attribute = Mock(PersistentAttribute)
        attribute.getName() >> attributeName
        def join = Mock(SqmAttributeJoin)
        join.getAttribute() >> attribute
        join.isFetched() >> fetched
        join.getSqmJoins() >> []
        join
    }

    def 'a sort by a property of an association joins the association with a left join'() {
        given:
        def root = Mock(SqmFrom)
        def clubJoin = Mock(SqmAttributeJoin)
        def namePath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('club.name')

        then: 'the path is not resolved as it is, which would make Hibernate join it with an inner join'
        _ * root.get('club') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> []
        1 * root.join('club', JoinType.LEFT) >> clubJoin
        1 * clubJoin.get('name') >> namePath
        result == namePath
    }

    def 'a sort by a nested association property joins every association on the path'() {
        given:
        def root = Mock(SqmFrom)
        def teamJoin = Mock(SqmAttributeJoin)
        def clubJoin = Mock(SqmAttributeJoin)
        def namePath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('team.club.name')

        then:
        _ * root.get('team') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> []
        1 * root.join('team', JoinType.LEFT) >> teamJoin
        _ * teamJoin.get('club') >> attributePath(Attribute.PersistentAttributeType.ONE_TO_ONE)
        _ * teamJoin.getSqmJoins() >> []
        1 * teamJoin.join('club', JoinType.LEFT) >> clubJoin
        1 * clubJoin.get('name') >> namePath
        result == namePath
    }

    def 'a sort reuses a join that the query already has for the association'() {
        given:
        def root = Mock(SqmFrom)
        def clubJoin = joinOf('club')
        def namePath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('club.name')

        then:
        _ * root.get('club') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> [clubJoin]
        0 * root.join(_, _)
        1 * clubJoin.get('name') >> namePath
        result == namePath
    }

    def 'a sort reuses the first join that the query made for the association'() {
        given:
        def root = Mock(SqmFrom)
        def fetch = joinOf('scores', true)
        def firstJoin = joinOf('scores')
        def secondJoin = joinOf('scores')
        def scorePath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('scores.score')

        then: 'the joins are read in the order in which they were made, which the set of getJoins() does not keep'
        _ * root.get('scores') >> attributePath(Attribute.PersistentAttributeType.ONE_TO_MANY)
        _ * root.getSqmJoins() >> [Mock(SqmJoin), joinOf('owner'), fetch, firstJoin, secondJoin]
        0 * root.getJoins()
        0 * root.join(_, _)
        1 * firstJoin.get('score') >> scorePath
        0 * secondJoin.get(_)

        and: 'a fetch is passed over for a join that the criteria of the query apply to'
        0 * fetch.get(_)
        result == scorePath
    }

    def 'a sort uses the join that the criteria of the query made for the association'() {
        given: 'two blocks of the owner each join its scores, and the query of the second block tracks its own'
        def owner = Mock(SqmFrom)
        def firstJoin = joinOf('scores')
        def secondJoin = joinOf('scores')
        secondJoin.getParent() >> owner
        def scorePath = Mock(SqmPath)
        def joinTracker = new JoinTracker(owner)
        joinTracker.addJoin('scores', secondJoin)
        def resolver = new ExpressionResolver(new AliasRegistry(), joinTracker)

        when:
        def result = resolver.resolveSort('scores.score')

        then: 'the join of the block comes before the first join that the source has for the association'
        _ * owner.get('scores') >> attributePath(Attribute.PersistentAttributeType.ONE_TO_MANY)
        _ * owner.getSqmJoins() >> [firstJoin, secondJoin]
        0 * owner.join(_, _)
        0 * firstJoin.get(_)
        1 * secondJoin.get('score') >> scorePath
        result == scorePath
    }

    def 'a sort does not use a tracked join that is made from another source'() {
        given:
        def root = Mock(SqmFrom)
        def firstJoin = joinOf('scores')
        def elsewhere = joinOf('scores')
        elsewhere.getParent() >> Mock(SqmFrom)
        def scorePath = Mock(SqmPath)
        def joinTracker = new JoinTracker(root)
        joinTracker.addJoin('scores', elsewhere)
        def resolver = new ExpressionResolver(new AliasRegistry(), joinTracker)

        when:
        def result = resolver.resolveSort('scores.score')

        then:
        _ * root.get('scores') >> attributePath(Attribute.PersistentAttributeType.ONE_TO_MANY)
        _ * root.getSqmJoins() >> [firstJoin]
        0 * elsewhere.get(_)
        1 * firstJoin.get('score') >> scorePath
        result == scorePath
    }

    def 'a sort uses the fetch of the association when the query has no other join of it'() {
        given:
        def root = Mock(SqmFrom)
        def fetch = joinOf('club', true)
        def namePath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('club.name')

        then: 'the association is not joined a second time'
        _ * root.get('club') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> [joinOf('owner'), fetch]
        0 * root.join(_, _)
        1 * fetch.get('name') >> namePath
        result == namePath
    }

    def 'a sort joins a nested association with the join type requested for its path'() {
        given:
        def root = Mock(SqmFrom)
        def teamJoin = Mock(SqmAttributeJoin)
        def clubJoin = Mock(SqmAttributeJoin)
        def namePath = Mock(SqmPath)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define('team.club', new HibernateAlias('team.club', 'team.club', JoinType.INNER, false))
        def resolver = new ExpressionResolver(aliasRegistry, new JoinTracker(root))

        when:
        def result = resolver.resolveSort('team.club.name')

        then:
        _ * root.get('team') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> []
        1 * root.join('team', JoinType.LEFT) >> teamJoin
        _ * teamJoin.get('club') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * teamJoin.getSqmJoins() >> []
        1 * teamJoin.join('club', JoinType.INNER) >> clubJoin
        1 * clubJoin.get('name') >> namePath
        result == namePath
    }

    def 'a sort through an alias starts from the join of the alias'() {
        given:
        def root = Mock(SqmFrom)
        def teamJoin = Mock(SqmAttributeJoin)
        def clubJoin = Mock(SqmAttributeJoin)
        def namePath = Mock(SqmPath)
        def aliasRegistry = new AliasRegistry()
        aliasRegistry.define('t', new HibernateAlias('team', 't', JoinType.INNER))
        def resolver = new ExpressionResolver(aliasRegistry, new JoinTracker(root))

        when:
        def result = resolver.resolveSort('t.club.name')

        then:
        1 * root.join('team', JoinType.INNER) >> teamJoin
        _ * teamJoin.get('club') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * teamJoin.getSqmJoins() >> []
        1 * teamJoin.join('club', JoinType.LEFT) >> clubJoin
        1 * clubJoin.get('name') >> namePath
        result == namePath
    }

    def 'a sort that ends with a foreign key reads it without a join of the association'() {
        given:
        def root = Mock(SqmFrom)
        def clubPath = attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        def idPath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('club.id', true)

        then: 'the foreign key is a column of the source, so the rows without a club are kept as it is'
        _ * root.get('club') >> clubPath
        _ * root.getSqmJoins() >> []
        _ * clubPath.get('id') >> idPath
        0 * root.join(_, _)
        result == idPath
    }

    def 'a sort that ends with a foreign key joins only the associations before it'() {
        given:
        def root = Mock(SqmFrom)
        def teamJoin = Mock(SqmAttributeJoin)
        def clubPath = attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        def idPath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('team.club.id', true)

        then:
        _ * root.get('team') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> []
        1 * root.join('team', JoinType.LEFT) >> teamJoin
        _ * teamJoin.get('club') >> clubPath
        _ * teamJoin.getSqmJoins() >> []
        _ * clubPath.get('id') >> idPath
        0 * teamJoin.join(_, _)
        result == idPath
    }

    def 'a sort that ends with a foreign key uses a join that the query has or requests for the association'() {
        given:
        def root = Mock(SqmFrom)
        def clubJoin = joinOf('club')
        def idPath = Mock(SqmPath)
        def aliasRegistry = new AliasRegistry()
        if (requested) {
            aliasRegistry.define('club', new HibernateAlias('club', 'club', JoinType.INNER, false))
        }
        def resolver = new ExpressionResolver(aliasRegistry, new JoinTracker(root))

        when:
        def result = resolver.resolveSort('club.id', true)

        then:
        _ * root.get('club') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> (requested ? [] : [clubJoin])
        (requested ? 1 : 0) * root.join('club', JoinType.INNER) >> clubJoin
        1 * clubJoin.get('id') >> idPath
        result == idPath

        where:
        requested << [false, true]
    }

    def 'a sort by the identifier of an association that has its key in the other table joins the association'() {
        given:
        def root = Mock(SqmFrom)
        def contractJoin = Mock(SqmAttributeJoin)
        def idPath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when: 'the path does not end with a foreign key'
        def result = resolver.resolveSort('contract.id')

        then: 'read as a plain path, the identifier would be joined with an implicit inner join'
        _ * root.get('contract') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * root.getSqmJoins() >> []
        1 * root.join('contract', JoinType.LEFT) >> contractJoin
        1 * contractJoin.get('id') >> idPath
        result == idPath
    }

    def 'a sort by a property of an embedded component is resolved as a plain path'() {
        given:
        def root = Mock(SqmFrom)
        def addressPath = attributePath(Attribute.PersistentAttributeType.EMBEDDED)
        def cityPath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('address.city')

        then:
        _ * root.get('address') >> addressPath
        _ * addressPath.get('city') >> cityPath
        0 * root.join(_)
        0 * root.join(_, _)
        result == cityPath
    }

    def 'a sort through an embedded component joins an association inside the component with a left join'() {
        given:
        def root = Mock(SqmFrom)
        def detailsJoin = Mock(SqmAttributeJoin)
        def authorJoin = Mock(SqmAttributeJoin)
        def namePath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('details.author.name')

        then: 'the component is joined, so that the association is not left to an implicit inner join'
        _ * root.get('details') >> attributePath(Attribute.PersistentAttributeType.EMBEDDED)
        _ * root.getSqmJoins() >> []
        1 * root.join('details') >> detailsJoin
        _ * detailsJoin.get('author') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        _ * detailsJoin.getSqmJoins() >> []
        1 * detailsJoin.join('author', JoinType.LEFT) >> authorJoin
        1 * authorJoin.get('name') >> namePath
        result == namePath
    }

    def 'a sort through an embedded component reuses the join that the query has for the component'() {
        given:
        def root = Mock(SqmFrom)
        def detailsJoin = joinOf('details')
        def authorJoin = Mock(SqmAttributeJoin)
        def namePath = Mock(SqmPath)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('details.author.name')

        then:
        _ * root.get('details') >> attributePath(Attribute.PersistentAttributeType.EMBEDDED)
        _ * root.getSqmJoins() >> [detailsJoin]
        0 * root.join(_)
        _ * detailsJoin.get('author') >> attributePath(Attribute.PersistentAttributeType.MANY_TO_ONE)
        1 * detailsJoin.join('author', JoinType.LEFT) >> authorJoin
        1 * authorJoin.get('name') >> namePath
        result == namePath
    }

    def 'a sort by a simple property is resolved as any other path'() {
        given:
        def root = Mock(From)
        def namePath = Mock(Path)
        def resolver = new ExpressionResolver(new AliasRegistry(), new JoinTracker(root))

        when:
        def result = resolver.resolveSort('name')

        then:
        1 * root.get('name') >> namePath
        0 * root.join(_, _)
        result == namePath
    }

    def "test resolve takes a path of a subquery from its own root when the parent joined the same name without an alias"() {
        given: "the owner joined by an association block of the outer query"
        def parentOwner = Mock(Join)
        def parentJoinTracker = new JoinTracker(Mock(From))
        parentJoinTracker.addJoin("owner", parentOwner)

        def subRoot = Mock(From)
        def ownerPath = Mock(Path)
        def firstNamePath = Mock(Path)
        def resolver = new ExpressionResolver(new AliasRegistry(new AliasRegistry()), new JoinTracker(parentJoinTracker, subRoot))

        when:
        def result = resolver.resolve("owner.firstName")

        then:
        0 * parentOwner.get(_)
        1 * subRoot.get("owner") >> ownerPath
        1 * ownerPath.get("firstName") >> firstNamePath
        result.is(firstNamePath)
    }
}
