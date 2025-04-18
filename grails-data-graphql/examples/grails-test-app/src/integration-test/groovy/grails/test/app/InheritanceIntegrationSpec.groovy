package grails.test.app

import grails.testing.mixin.integration.Integration
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.grails.web.json.JSONArray
import spock.lang.Specification

@Integration
class InheritanceIntegrationSpec extends Specification implements GraphQLSpec {

    void "test the ... on directive works"() {
        when:
        def resp = graphQL.graphql("""
            {
                mammalList {
                    id
                    name
                    ... on LandMammal {
                        limbCount
                        moveSpeed
                    }
                    ... on Human {
                        language
                    }
                    ... on Dog {
                        barks
                    }
                    ... on Labradoodle {
                        cutenessLevel
                    }
                }
            }
        """, String.class)
        String data = resp.getBody().get()

        then:
        data == '{"data":{"mammalList":[{"id":1,"name":"Spot","barks":true},{"id":2,"name":"Chloe","cutenessLevel":100},{"id":3,"name":"Kotlin Ken","language":true}]}}'
    }
}
