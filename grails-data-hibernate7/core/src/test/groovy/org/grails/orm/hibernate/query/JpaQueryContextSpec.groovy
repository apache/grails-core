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
import jakarta.persistence.criteria.Join
import jakarta.persistence.criteria.JoinType
import jakarta.persistence.criteria.Path
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
}
