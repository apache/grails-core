/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License')
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.mapping.core

import spock.lang.Specification

class ThreadLocalSessionResolverSpec extends Specification {

    ThreadLocalSessionResolver<Session> resolver = new ThreadLocalSessionResolver<>()

    def 'should bind and resolve session'() {
        given:
        Session session = Mock(Session)

        when:
        resolver.bind(session)

        then:
        resolver.resolve() == session

        cleanup:
        resolver.unbind()
    }

    def 'should bind and resolve qualified session'() {
        given:
        Session session = Mock(Session)
        String qualifier = 'secondary'

        when:
        resolver.bind(qualifier, session)

        then:
        resolver.resolve(qualifier) == session

        cleanup:
        resolver.unbind(qualifier)
    }

    def 'should unbind session'() {
        given:
        Session session = Mock(Session)
        resolver.bind(session)

        when:
        resolver.unbind()

        then:
        resolver.resolve() == null
    }
}
