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

import jakarta.persistence.criteria.JoinType
import spock.lang.Specification

class JoinPlanSpec extends Specification {

    def 'a path nobody names has no join type'() {
        expect:
        new JoinPlan().typeOf('team') == null
    }

    def 'a declaration decides the join type of its path, and an inner one beats a left one'() {
        given:
        def plan = new JoinPlan()

        when:
        types.each { plan.declare('team.likes', it) }

        then:
        plan.typeOf('team.likes') == expected

        where:
        types                          || expected
        [JoinType.LEFT]                || JoinType.LEFT
        [JoinType.INNER]               || JoinType.INNER
        [JoinType.LEFT, JoinType.INNER] || JoinType.INNER
        [JoinType.INNER, JoinType.LEFT] || JoinType.INNER
    }

    def 'a dotted property alone gives an inner join'() {
        given:
        def plan = new JoinPlan()

        when:
        plan.mention('team.likes')

        then:
        plan.typeOf('team.likes') == JoinType.INNER
        plan.typeOf('team') == JoinType.INNER
    }

    def 'a declaration of the path beats a dotted property that goes through it'() {
        given:
        def plan = new JoinPlan()

        when:
        plan.declare('team.likes', JoinType.LEFT)
        plan.mention('team.likes')

        then:
        plan.typeOf('team.likes') == JoinType.LEFT
    }

    def 'a path takes the join type of the paths below it, and a dotted property through it needs it inner'() {
        given:
        def plan = new JoinPlan()

        when:
        plan.declare('team.likes.tag', JoinType.LEFT)

        then: 'nobody names the paths before the last one, so they follow the alias'
        plan.typeOf('team.likes') == JoinType.LEFT
        plan.typeOf('team') == JoinType.LEFT

        when: 'a dotted property goes through one of them'
        plan.mention('team.likes')

        then: 'it keeps its inner join, which also needs the path before it, while the alias path stays left'
        plan.typeOf('team.likes') == JoinType.INNER
        plan.typeOf('team') == JoinType.INNER
        plan.typeOf('team.likes.tag') == JoinType.LEFT
    }

    def 'an inner declaration below a path requires an inner join of the path'() {
        given:
        def plan = new JoinPlan()

        when:
        plan.declare('team.likes.tag', JoinType.LEFT)
        plan.declare('team.likes.tag.owner', JoinType.INNER)

        then:
        plan.typeOf('team.likes.tag') == JoinType.LEFT
        plan.typeOf('team.likes') == JoinType.INNER
        plan.typeOf('team') == JoinType.INNER
    }

    def 'a configured join type is recorded for blocks but declares nothing by itself'() {
        given:
        def plan = new JoinPlan()

        when:
        plan.configure('team.favorite', JoinType.LEFT)

        then:
        plan.configured('team.favorite') == JoinType.LEFT
        plan.configured('team') == null
        plan.typeOf('team.favorite') == null
        plan.configuredTypes() == ['team.favorite': JoinType.LEFT]
    }

    def 'a join type configured after the aliases replaces the declared one'() {
        given:
        def plan = new JoinPlan()
        plan.declare('team.likes', JoinType.INNER)

        when:
        plan.override('team.likes', JoinType.LEFT)

        then:
        plan.typeOf('team.likes') == JoinType.LEFT
        plan.typeOf('team') == JoinType.INNER
    }
}
