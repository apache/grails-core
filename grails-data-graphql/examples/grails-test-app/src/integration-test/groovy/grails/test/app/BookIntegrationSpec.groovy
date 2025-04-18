package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Specification

@Integration
class BookIntegrationSpec extends Specification implements GraphQLSpec {

    void "test books cannot be queried directly"() {
        when:
        def resp = graphQL.graphql("""
            {
              bookList {
                id
              }
            }
        """)

        def result = resp.body()

        then:
        result.errors.size() == 1
        result.errors[0].message == "Validation error (FieldUndefined@[bookList]) : Field 'bookList' in type 'Query' is undefined"
    }

}
