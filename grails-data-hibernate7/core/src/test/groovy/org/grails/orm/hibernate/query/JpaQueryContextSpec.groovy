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
}
