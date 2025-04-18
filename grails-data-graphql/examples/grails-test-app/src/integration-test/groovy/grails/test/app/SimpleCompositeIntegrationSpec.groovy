package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class SimpleCompositeIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                simpleCompositeCreate(simpleComposite: {
                    title: "x",
                    description: "y",
                    someUUID: "20666c44-f42a-4db2-935d-a97af6646c77"
                }) {
                    title
                    description
                    someUUID
                }
            }
        """)
        Map obj = resp.body().data.simpleCompositeCreate

        then:
        obj.title == 'x'
        obj.description == 'y'
        obj.someUUID == '20666c44-f42a-4db2-935d-a97af6646c77'
    }

    void "test updating an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                simpleCompositeUpdate(title: "x", description: "y", simpleComposite: {
                    someUUID: "8e22054f-a419-44dd-8726-1e53023cb7be"
                }) {
                    title
                    description
                    someUUID
                }
            }
        """)
        Map obj = resp.body().data.simpleCompositeUpdate

        then:
        obj.title == 'x'
        obj.description == 'y'
        obj.someUUID == '8e22054f-a419-44dd-8726-1e53023cb7be'
    }

    void "test retrieving an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            {
                simpleComposite(title: "x", description: "y") {
                    title
                    description
                    someUUID
                }
            }
        """)
        Map obj = resp.body().data.simpleComposite

        then:
        obj.title == 'x'
        obj.description == 'y'
        obj.someUUID == '8e22054f-a419-44dd-8726-1e53023cb7be'
    }

    void "test listing entities with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            {
                simpleCompositeList {
                    title
                    description
                    someUUID
                }
            }
        """)
        List obj = resp.body().data.simpleCompositeList

        then:
        obj.size() == 1
        obj[0].title == 'x'
        obj[0].description == 'y'
        obj[0].someUUID == '8e22054f-a419-44dd-8726-1e53023cb7be'
    }

    void "test deleting an entity with a simple composite id"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                simpleCompositeDelete(title: "x", description: "y") {
                    success
                }
            }
        """)
        Map obj = resp.body().data.simpleCompositeDelete

        then:
        obj.success
    }
}
