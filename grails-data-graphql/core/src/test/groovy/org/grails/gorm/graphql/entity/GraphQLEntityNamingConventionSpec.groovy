package org.grails.gorm.graphql.entity

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.types.GraphQLPropertyType
import spock.lang.Shared
import spock.lang.Specification

class GraphQLEntityNamingConventionSpec extends Specification {

    @Shared GraphQLEntityNamingConvention namingConvention
    @Shared PersistentEntity entity

    @SuppressWarnings('UnnecessaryGetter')
    void setupSpec() {
        namingConvention = new GraphQLEntityNamingConvention()
        entity = Stub(PersistentEntity) {
            getDecapitalizedName() >> 'foo'
            getJavaClass() >> Foo
        }
    }

    void "test naming conventions"() {
        expect:
        namingConvention.getGet(entity) == 'foo'
        namingConvention.getList(entity) == 'fooList'
        namingConvention.getCreate(entity) == 'fooCreate'
        namingConvention.getUpdate(entity) == 'fooUpdate'
        namingConvention.getDelete(entity) == 'fooDelete'

        when:
        String name = namingConvention.getType(entity, type)

        then:
        name == expected

        where:
        type                                | expected
        GraphQLPropertyType.CREATE          | 'FooCreate'
        GraphQLPropertyType.CREATE_EMBEDDED | 'FooCreateEmbedded'
        GraphQLPropertyType.CREATE_NESTED   | 'FooCreateNested'
        GraphQLPropertyType.UPDATE          | 'FooUpdate'
        GraphQLPropertyType.UPDATE_EMBEDDED | 'FooUpdateEmbedded'
        GraphQLPropertyType.UPDATE_NESTED   | 'FooUpdateNested'
        GraphQLPropertyType.OUTPUT          | 'Foo'
        GraphQLPropertyType.OUTPUT_EMBEDDED | 'FooEmbedded'
    }

    class Foo {

    }
}
