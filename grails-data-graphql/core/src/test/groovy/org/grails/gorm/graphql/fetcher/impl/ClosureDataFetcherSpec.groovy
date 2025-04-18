package org.grails.gorm.graphql.fetcher.impl

import graphql.schema.DataFetchingEnvironment
import spock.lang.Specification
import spock.lang.Subject

@Subject(ClosureDataFetcher)
class ClosureDataFetcherSpec extends Specification {

    void "test closure is called with the source argument"() {
        given:
        String result
        Closure closure = { String s ->
            result = s
        }

        when:
        new ClosureDataFetcher(closure).get(Mock(DataFetchingEnvironment) {
            1 * getSource() >> 'foo'
        })

        then:
        result == 'foo'
    }

    void "test a no arg closure works"() {
        given:
        String result
        Closure closure = {
            result = 'hello'
        }

        when:
        new ClosureDataFetcher(closure).get(Mock(DataFetchingEnvironment) {
            1 * getSource() >> 'foo'
        })

        then:
        result == 'hello'
    }
}
