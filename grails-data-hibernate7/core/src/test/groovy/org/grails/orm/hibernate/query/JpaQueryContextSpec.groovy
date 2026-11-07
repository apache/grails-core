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

import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.From
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.ParameterExpression
import jakarta.persistence.criteria.Path
import jakarta.persistence.metamodel.Attribute
import jakarta.persistence.metamodel.SingularAttribute
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria
import org.grails.datastore.mapping.model.types.Association
import org.hibernate.query.sqm.tree.domain.SqmPath
import org.hibernate.query.sqm.tree.from.SqmFrom
import spock.lang.Specification

/**
 * Unit tests for JpaQueryContext as an orchestrator.
 */
class JpaQueryContextSpec extends Specification {

    def "test root management"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)

        expect:
        context.getRoot() == root
        context.getFullyQualifiedExpression("root") == root
        context.getFullyQualifiedExpression("{alias}") == root
    }

    def "test alias registration and resolution"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)
        def faceJoin = Mock(Join)
        def namePath = Mock(Path)

        when: "defining an alias"
        context.registerAlias("f", new HibernateAlias("face", "f", JoinType.INNER))

        then:
        context.hasAlias("f")
        context.getAliasedExpression("f") == null // Not realized yet

        when: "resolving a path through the alias"
        def result = context.getFullyQualifiedExpression("f.name")

        then:
        1 * root.join("face", JoinType.INNER) >> faceJoin
        1 * faceJoin.get("name") >> namePath
        result == namePath
        context.getAliasedExpression("f") == faceJoin
    }

    def "test subquery context delegation"() {
        given:
        def parentRoot = Mock(From)
        def parentContext = new JpaQueryContext(parentRoot)
        def faceJoin = Mock(Join)
        parentContext.registerAlias("f", faceJoin)
        parentContext.addFrom("f", faceJoin)

        def subRoot = Mock(From)
        def subContext = JpaQueryContext.forSubquery(parentContext, subRoot)

        def namePath = Mock(Path)

        when: "resolving parent alias in subquery"
        def result = subContext.getFullyQualifiedExpression("f.name")

        then:
        0 * subRoot.join(_, _)
        1 * faceJoin.get("name") >> namePath
        result == namePath
    }

    def "test addFrom tracking"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)
        def join = Mock(Join)

        when:
        context.addFrom("nicknames", join)

        then:
        context.getFrom("nicknames") == join
        context.getFullyQualifiedExpression("nicknames") == join
    }

    def 'association paths and named aliases have independent caches'() {
        given:
        def root = Mock(From)
        def favoriteJoin = Mock(Join)
        def likesJoin = Mock(Join)
        def context = new JpaQueryContext([
                new HibernateAlias('favorite', 'f'),
                new HibernateAlias('likes', 'favorite')
        ], root)

        when:
        def aliasJoin = context.getFullyQualifiedExpression('favorite')
        def pathJoin = context.resolveAssociation('favorite')

        then:
        1 * root.join('likes', JoinType.INNER) >> likesJoin
        1 * root.join('favorite', JoinType.INNER) >> favoriteJoin
        aliasJoin == likesJoin
        pathJoin == favoriteJoin
        context.getFrom('favorite') == favoriteJoin
        context.getFrom('likes') == likesJoin
        context.getFrom('f') == null
        context.getFullyQualifiedExpression('f') == favoriteJoin
        context.getFullyQualifiedExpression('favorite') == likesJoin
    }

    def "test aliases-only and parent-only constructor overloads"() {
        given:
        def root = Mock(From)
        def alias = new HibernateAlias("face", "f", JoinType.INNER)

        when: "constructed with aliases and root but no parent"
        def aliasesContext = new JpaQueryContext([alias], root)

        then:
        aliasesContext.hasAlias("f")
        aliasesContext.getRoot() == root

        when: "constructed with a parent and root but no aliases"
        def parentContext = new JpaQueryContext(root)
        def childRoot = Mock(From)
        def childContext = new JpaQueryContext(parentContext, childRoot)

        then:
        childContext.getRoot() == childRoot
        childContext.getFullyQualifiedExpression("{alias}") == root
    }

    def "test forRoot with aliases static factory"() {
        given:
        def root = Mock(From)
        def alias = new HibernateAlias("face", "f", JoinType.INNER)

        when:
        def context = JpaQueryContext.forRoot([alias], root)

        then:
        context.hasAlias("f")
        context.getRoot() == root
    }

    def "test setParent reparents an existing context"() {
        given:
        def parentRoot = Mock(From)
        def parentContext = new JpaQueryContext(parentRoot)
        def faceJoin = Mock(Join)
        parentContext.registerAlias("f", faceJoin)
        def orphanContext = new JpaQueryContext(Mock(From))

        expect:
        !orphanContext.hasAlias("f")

        when:
        orphanContext.setParent(parentContext)

        then:
        orphanContext.hasAlias("f")
    }

    def "test getAliasedExpression delegates to the parent when not realized locally"() {
        given:
        def parentRoot = Mock(From)
        def parentContext = new JpaQueryContext(parentRoot)
        def faceJoin = Mock(Join)
        parentContext.registerAlias("f", faceJoin)
        def subContext = JpaQueryContext.forSubquery(parentContext, Mock(From))

        expect:
        subContext.getAliasedExpression("f") == faceJoin
    }

    def "test alias-token resolution delegates to the parent root and path"() {
        given:
        def parentRoot = Mock(From)
        def parentContext = new JpaQueryContext(parentRoot)
        def subRoot = Mock(From)
        def subContext = JpaQueryContext.forSubquery(parentContext, subRoot)
        def namePath = Mock(Path)

        expect:
        subContext.getFullyQualifiedExpression("{alias}") == parentRoot
        subContext.getFullyQualifiedPath("{alias}") == parentRoot

        when:
        def resolved = subContext.getFullyQualifiedPath("{alias}.name")

        then:
        1 * parentRoot.get("name") >> namePath
        resolved == namePath
    }

    def "test clone produces a distinct context instance"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)

        when:
        def cloned = context.clone()

        then:
        !cloned.is(context)
        cloned.getRoot() == root
    }

    def "test parameter values are recorded in order"() {
        given:
        def context = new JpaQueryContext(Mock(From))
        def first = Mock(ParameterExpression)
        def second = Mock(ParameterExpression)

        when:
        context.bindParameter(first, 'a')
        context.bindParameter(second, 'b')

        then:
        context.getParameterValues().keySet().toList() == [first, second]
        context.getParameterValues().values().toList() == ['a', 'b']
    }

    def "test subquery parameter values are recorded in the parent context"() {
        given:
        def parentContext = new JpaQueryContext(Mock(From))
        def subContext = JpaQueryContext.forSubquery(parentContext, Mock(From))
        def parentParameter = Mock(ParameterExpression)
        def subParameter = Mock(ParameterExpression)

        when:
        parentContext.bindParameter(parentParameter, 'outer')
        subContext.bindParameter(subParameter, 'inner')

        then:
        parentContext.getParameterValues() == [(parentParameter): 'outer', (subParameter): 'inner']
        subContext.getParameterValues() == parentContext.getParameterValues()
    }

    def "test a selection alias names the selection but does not resolve a property path"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)
        def nested = new JpaQueryContext(context, Mock(From))
        def selection = Mock(Expression)
        def heightPath = Mock(Path)

        when:
        context.registerSelectionAlias('height', selection)

        then:
        context.getSelectionAlias('height') == selection
        context.getSelectionAlias('other') == null
        nested.getSelectionAlias('height') == null
        !context.hasAlias('height')

        when:
        def resolved = context.getFullyQualifiedExpression('height')

        then:
        1 * root.get('height') >> heightPath
        resolved == heightPath
    }

    def "test the context of an association block is recorded in the context of the query"() {
        given:
        def context = new JpaQueryContext(Mock(From))
        def outerContext = new JpaQueryContext(context, Mock(From))
        def blockContext = new JpaQueryContext(outerContext, Mock(From))
        def block = new DetachedAssociationCriteria(Object, Mock(Association))
        def other = new DetachedAssociationCriteria(Object, Mock(Association))

        when: 'the context of a nested block is recorded'
        outerContext.registerAssociationContext(block, blockContext)

        then: 'the context of the query has it, so that an order of the query finds the block at any depth'
        context.getAssociationContext(block).is(blockContext)
        outerContext.getAssociationContext(block).is(blockContext)

        and: 'a block that the query has not built has no context'
        context.getAssociationContext(other) == null
    }

    def "test a path of the root or of one of its components is a column of the root"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)
        def componentAttribute = Mock(Attribute)
        componentAttribute.getPersistentAttributeType() >> Attribute.PersistentAttributeType.EMBEDDED
        def componentJoin = Mock(Join)
        componentJoin.getAttribute() >> componentAttribute
        componentJoin.getParent() >> root
        def associationPath = Mock(Path)
        associationPath.getParentPath() >> root
        def name = Mock(Path)
        name.getParentPath() >> root
        def foreignKey = Mock(Path)
        foreignKey.getParentPath() >> associationPath
        def componentProperty = Mock(Path)
        componentProperty.getParentPath() >> componentJoin

        expect:
        context.isColumnOfRoot(name)
        context.isColumnOfRoot(foreignKey)
        context.isColumnOfRoot(componentProperty)
    }

    def "test a path through the join of an association is not a column of the root"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)
        def associationAttribute = Mock(Attribute)
        associationAttribute.getPersistentAttributeType() >> Attribute.PersistentAttributeType.MANY_TO_ONE
        def associationJoin = Mock(Join)
        associationJoin.getAttribute() >> associationAttribute
        associationJoin.getParent() >> root
        def joined = Mock(Path)
        joined.getParentPath() >> associationJoin
        def detached = Mock(Path)

        expect:
        !context.isColumnOfRoot(joined)
        !context.isColumnOfRoot(detached)
        !context.isColumnOfRoot(Mock(Expression))
    }

    def "test a sort path that ends with a foreign key is not joined for it"() {
        given:
        def root = Mock(From)
        def context = new JpaQueryContext(root)
        def attribute = Mock(SingularAttribute)
        attribute.getPersistentAttributeType() >> Attribute.PersistentAttributeType.MANY_TO_ONE
        attribute.isAssociation() >> true
        def clubPath = Mock(SqmPath)
        clubPath.getModel() >> attribute
        def idPath = Mock(SqmPath)
        def sqmRoot = Mock(SqmFrom)
        def sqmContext = new JpaQueryContext(sqmRoot)

        when:
        def result = sqmContext.getSortExpression('club.id', true)

        then:
        _ * sqmRoot.get('club') >> clubPath
        _ * sqmRoot.getSqmJoins() >> []
        _ * clubPath.get('id') >> idPath
        0 * sqmRoot.join(_, _)
        result == idPath
    }

    def "test parameter values cannot be changed through the returned map"() {
        given:
        def context = new JpaQueryContext(Mock(From))

        when:
        context.getParameterValues().put(Mock(ParameterExpression), 'value')

        then:
        thrown(UnsupportedOperationException)
    }
}
