package grails.test.app

import grails.testing.mixin.integration.Integration
import grails.testing.spock.OnceBefore
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import spock.lang.Shared
import spock.lang.Specification

@Integration
class ArguedFieldIntegrationSpec extends Specification implements GraphQLSpec {

    @Shared Long grailsId

    @OnceBefore
    void createDomain() {
        ArguedField.withTransaction {
            grailsId = new ArguedField(name: 'test').save(flush: true).id
        }

    }

    void "test a simple argument"() {
        when:
        def resp = graphQL.graphql("""
            {
              arguedField(id: ${grailsId}) {
                  withArgument(ping: "PONG")
              }
            }
        """)
        def obj = resp.body().data.arguedField

        then:
        obj.withArgument == "PONG"
    }

    void "test a simple argument list"() {
        when:
        def resp = graphQL.graphql("""
            {
              arguedField(id: ${grailsId}) {
                  withArgumentList(pings: ["P", "O", "N", "G" ])
              }
            }
        """)
        def obj = resp.body().data.arguedField

        then:
        obj.withArgumentList == "P-O-N-G"
    }

    void "test a custom argument"() {
        when:
        def resp = graphQL.graphql("""
            {
              arguedField(id: ${grailsId}) {
                  withCustomArgument(ping: {payload: "PONG"})
              }
            }
        """)
        def obj = resp.body().data.arguedField

        then:
        obj.withCustomArgument == "PONG"
    }

}
