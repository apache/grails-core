package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import org.grails.web.json.JSONArray
import spock.lang.Shared
import spock.lang.Specification

@Integration
class SoftDeleteIntegrationSpec extends Specification implements GraphQLSpec {

    @Shared
    Long id

    @OnceBefore
    void createInstance() {
        def resp = graphQL.graphql("""
            mutation {
              softDeleteCreate(softDelete: {
                name: "foo"
              }) {
                id
              }
            }
        """)
        id = resp.body().data.softDeleteCreate.id
        assert id != null
    }

    void "test we can query the instance"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDelete(id: $id) {
                name
              }
            }
        """)
        def json = resp.body().data.softDelete

        then:
        json.name == 'foo'
    }

    void "test we can get the instance in a list query"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDeleteList {
                name
              }
            }
        """)
        List json = resp.body().data.softDeleteList

        then:
        json.size() == 1
        json[0].name == 'foo'
    }

    void "test delete"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              softDeleteDelete(id: $id) {
                success
              }
            }
        """)
        def json = resp.body().data.softDeleteDelete
        SoftDelete softDelete
        SoftDelete.withNewSession {
            softDelete = SoftDelete.get(id)
        }

        then:
        json.success
        !softDelete.active
        softDelete.name == "foo"
    }

    void "test we cant query the instance"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDelete(id: $id) {
                name
              }
            }
        """)
        def json = resp.body().data.softDelete

        then:
        json == null
    }

    void "test we cant get the instance in a list query"() {
        when:
        def resp = graphQL.graphql("""
            {
              softDeleteList {
                name
              }
            }
        """)
        List json = resp.body().data.softDeleteList

        then:
        json.empty
    }
}
