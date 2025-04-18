package grails.test.app

import grails.testing.mixin.integration.Integration
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import spock.lang.Specification

@Integration
class GrailsTeamMemberIntegrationSpec extends Specification implements GraphQLSpec {

    void "test retrieving a page of results"() {
        def resp = graphQL.graphql("""
            {           
                grailsTeamMemberList(max: 5, offset: 0, sort: "name") {
                    results {
                        name
                    }
                    totalCount
                }
            }
        """)
        Map data = resp.body().data.grailsTeamMemberList
        JSONArray results = data.results

        expect:
        data.totalCount == 16
        results.size() == 5
        results[0].name == 'Alvaro'
        results[1].name == 'Ben'
        results[2].name == 'Colin'
        results[3].name == 'Dave'
        results[4].name == 'Graeme'
    }

    void "test retrieving the next page of results"() {
        def resp = graphQL.graphql("""
            {           
                grailsTeamMemberList(max: 5, offset: 5, sort: "name") {
                    results {
                        name
                    }
                    totalCount
                }
            }
        """)
        Map data = resp.body().data.grailsTeamMemberList
        JSONArray results = data.results

        expect:
        data.totalCount == 16
        results.size() == 5
        results[0].name == 'Ivan'
        results[1].name == 'Jack'
        results[2].name == 'James'
        results[3].name == 'Jeff'
        results[4].name == 'Matthew'
    }

}
