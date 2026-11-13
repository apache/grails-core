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
import jakarta.persistence.criteria.JoinType
import spock.lang.Specification

/**
 * Unit tests for JoinTracker.
 */
class JoinTrackerSpec extends Specification {

    def "test root and joins"() {
        given:
        def root = Mock(From)
        def tracker = new JoinTracker(root)
        def join = Mock(From)

        when:
        tracker.addJoin("face", join)

        then:
        tracker.getRoot() == root
        tracker.getJoin("face") == join
    }

    def "test parent delegation"() {
        given:
        def parentRoot = Mock(From)
        def parent = new JoinTracker(parentRoot)
        def subRoot = Mock(From)
        def child = new JoinTracker(parent, subRoot)
        
        def parentJoin = Mock(From)

        when:
        parent.addJoin("face", parentJoin)

        then:
        child.getJoin("face") == parentJoin
        child.getRoot() == subRoot
    }

    def "test child override join"() {
        given:
        def parentRoot = Mock(From)
        def parent = new JoinTracker(parentRoot)
        def subRoot = Mock(From)
        def child = new JoinTracker(parent, subRoot)
        
        def parentJoin = Mock(From)
        def childJoin = Mock(From)

        when:
        parent.addJoin("face", parentJoin)
        child.addJoin("face", childJoin)

        then:
        child.getJoin("face") == childJoin
        parent.getJoin("face") == parentJoin
        child.getJoin('missing') == null
        parent.getJoin('missing') == null
    }

    def 'an association block shares its joins with its query by the path from the root of the query'() {
        given:
        def query = new JoinTracker(Mock(From))
        def team = new JoinTracker(query, Mock(From)) // a subquery has joins of its own
        def teamJoin = Mock(From)
        def likesJoin = Mock(From)
        def block = JoinTracker.forBlock(query, Mock(From), 'team')
        def nested = JoinTracker.forBlock(block, Mock(From), 'likes')

        when:
        query.addJoin('team', teamJoin)
        nested.addJoin('tag', likesJoin)
        block.addJoin('likes', Mock(From))

        then:
        block.getOwnJoin('likes') == query.getOwnJoin('team.likes')
        nested.getOwnJoin('tag') == query.getOwnJoin('team.likes.tag')
        nested.getOwnJoin('tag') == likesJoin
        block.getOwnJoin('likes.tag') == likesJoin
        query.getOwnJoin('team') == teamJoin
        team.getOwnJoin('team.likes') == null
    }

    def 'the root of each scope is its own'() {
        given:
        def queryRoot = Mock(From)
        def blockRoot = Mock(From)
        def query = new JoinTracker(queryRoot)
        def block = JoinTracker.forBlock(query, blockRoot, 'team')

        expect:
        query.getOwnJoin('root') == queryRoot
        block.getOwnJoin('root') == blockRoot
        block.getRoot() == blockRoot
    }

    def 'a block reads the join types the criteria of the whole query decided by its path from the root'() {
        given:
        def plan = new JoinPlan()
        plan.declare('team.likes', JoinType.LEFT)
        def query = new JoinTracker(Mock(From))
        def block = JoinTracker.forBlock(query, Mock(From), 'team')
        def nested = JoinTracker.forBlock(block, Mock(From), 'likes')

        when:
        query.setPlan(plan)

        then:
        query.plannedType('team.likes') == JoinType.LEFT
        block.plannedType('likes') == JoinType.LEFT
        nested.plannedType('tag') == null
        nested.plannedType('likes') == null
    }

    def 'a tracker without a plan has no planned join type'() {
        expect:
        new JoinTracker(Mock(From)).plannedType('team') == null
    }
}
