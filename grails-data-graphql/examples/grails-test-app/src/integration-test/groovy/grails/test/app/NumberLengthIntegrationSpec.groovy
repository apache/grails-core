package grails.test.app

import grails.testing.mixin.integration.Integration
import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import org.grails.web.json.JSONObject
import spock.lang.Shared
import spock.lang.Specification

@Integration
class NumberLengthIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating with numbers valid"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data.numberLengthCreate.id
    }

    void "test creating with numbers too long long"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${BigInteger.valueOf(Long.MAX_VALUE.longValue()) + 1}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }

    void "test creating with numbers too long short"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE + 1},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }

    void "test creating with numbers too long int"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Long.valueOf(Integer.MAX_VALUE) + 1},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }

    void "test creating with numbers too long byte"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
                numberLengthCreate(numberLength: {
                    aByte: ${Byte.MAX_VALUE + 1},
                    aShort: ${Short.MAX_VALUE},
                    anInt: ${Integer.MAX_VALUE},
                    aLong: ${Long.MAX_VALUE}
                }) {
                    id
                }
            }
        """)
        Map data = resp.body()

        then:
        data.data == null
        data.errors.size() == 1
    }
}
