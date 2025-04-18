package org.grails.gorm.graphql.entity.dsl

import spock.lang.Specification

/**
 * Created by jameskleeh on 7/23/17.
 */
class LazyGraphQLMappingSpec extends Specification {

    void "test lazy mapping"() {
        when:
        def mapping = GraphQLMapping.lazy {
            throw new Exception()
        }

        then:
        noExceptionThrown()
        mapping instanceof LazyGraphQLMapping

        when:
        mapping.initialize()

        then:
        thrown(Exception)
    }
}
