package org.grails.gorm.graphql.types

import graphql.Scalars
import graphql.scalars.ExtendedScalars
import graphql.schema.*
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.GraphQL
import org.grails.gorm.graphql.HibernateSpec
import org.grails.gorm.graphql.domain.general.toone.One
import org.grails.gorm.graphql.entity.GraphQLEntityNamingConvention
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.response.errors.DefaultGraphQLErrorsResponseHandler
import org.grails.gorm.graphql.response.pagination.DefaultGraphQLPaginationResponseHandler
import org.grails.gorm.graphql.testing.GraphQLSchemaSpec
import org.grails.gorm.graphql.types.input.AbstractInputObjectTypeBuilder
import org.grails.gorm.graphql.types.output.AbstractObjectTypeBuilder
import org.grails.gorm.graphql.types.scalars.CustomScalars
import org.springframework.context.support.StaticMessageSource
import spock.lang.Shared
import spock.lang.Unroll

import java.sql.Time
import java.sql.Timestamp
import java.util.concurrent.atomic.AtomicLong

class DefaultGraphQLTypeManagerSpec extends HibernateSpec implements GraphQLSchemaSpec {

    List<Class> getDomainClasses() { [One] }

    DefaultGraphQLTypeManager typeManager
    @Shared
    GraphQLCodeRegistry.Builder codeRegistry

    void setup() {
        codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        typeManager = new DefaultGraphQLTypeManager(codeRegistry,
                new GraphQLEntityNamingConvention(),
                new DefaultGraphQLErrorsResponseHandler(new StaticMessageSource(), codeRegistry),
                new DefaultGraphQLDomainPropertyManager(),
                new DefaultGraphQLPaginationResponseHandler())


    }

    @Unroll
    void "test default type exists for #clazz"() {
        when:
        typeManager.getType(clazz)
        codeRegistry.build()

        then:
        notThrown(TypeNotFoundException)

        where:
        clazz << [Integer, Long, Short, Byte, Byte[], Double, Float, BigInteger, BigDecimal, String, Boolean, Character, Character[], UUID, URL, URI, Time, java.sql.Date, Timestamp, Currency, TimeZone]
    }

    @Unroll
    void "test getType works with primitive type #clazz"() {
        when:
        GraphQLType type = typeManager.getType(clazz)
        codeRegistry.build()

        then:
        type == expectedType

        where:
        clazz   | expectedType
        boolean | Scalars.GraphQLBoolean
        int     | Scalars.GraphQLInt
        short   | ExtendedScalars.GraphQLShort
        byte    | ExtendedScalars.GraphQLByte
        char    | ExtendedScalars.GraphQLChar
        long    | ExtendedScalars.GraphQLLong
        float   | Scalars.GraphQLFloat
        double  | Scalars.GraphQLFloat
    }

    @Unroll
    void "test getType works with primitive array type #clazz"() {
        when:
        GraphQLType type = typeManager.getType(clazz)
        codeRegistry.build()

        then:
        expectedType.isAssignableFrom(type.class)

        where:
        clazz  | expectedType
        byte[] | CustomScalars.GraphQLByteArray.class
        char[] | CustomScalars.GraphQLCharacterArray.class
    }

    void "test getType nullable false"() {
        when:
        GraphQLType type = typeManager.getType(String, false)
        codeRegistry.build()

        then:
        type instanceof GraphQLNonNull
        unwrap(null, type) == Scalars.GraphQLString
    }

    void "test registerType"() {
        when:
        typeManager.getType(AtomicLong)
        codeRegistry.build()

        then:
        thrown(TypeNotFoundException)

        when:
        codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        typeManager.registerType(AtomicLong, ExtendedScalars.GraphQLLong)
        GraphQLType type = typeManager.getType(AtomicLong)
        codeRegistry.build()

        then:
        type == ExtendedScalars.GraphQLLong
    }

    void "test getEnumType Foo"() {
        when:
        GraphQLEnumType enumType = (GraphQLEnumType) typeManager.getEnumType(Foo, true)
        codeRegistry.build()

        then:
        enumType.description == 'Foo type'
        enumType.name == 'Foo'
        enumType.values[0].name == 'X'
        enumType.values[0].description == null
        enumType.values[0].deprecationReason == 'Deprecated'
        enumType.values[0].deprecated
        enumType.values[1].name == 'Y'
        enumType.values[1].description == null
        enumType.values[1].deprecationReason == 'Y is deprecated'
        enumType.values[1].deprecated
        enumType.values[2].name == 'Z'
        enumType.values[2].description == 'This is the Z property'
        enumType.values[2].deprecationReason == null
        !enumType.values[2].deprecated
    }

    void "test getEnumType Bar"() {
        when:
        GraphQLEnumType enumType = (GraphQLEnumType) typeManager.getEnumType(Bar, true)
        codeRegistry.build()

        then:
        enumType.description == null
        enumType.name == 'Bar'
        enumType.values[0].name == 'A'
        enumType.values[0].description == null
        enumType.values[0].deprecationReason == null
        !enumType.values[0].deprecated
    }

    void "test getQueryType"() {
        given:
        PersistentEntity entity = One.gormPersistentEntity
        GraphQLObjectType type = GraphQLObjectType.newObject().name("One").field(GraphQLFieldDefinition.newFieldDefinition().name('foo').type(Scalars.GraphQLString).build()).build()
        typeManager.objectTypeBuilders.put(GraphQLPropertyType.OUTPUT, Mock(AbstractObjectTypeBuilder) {
            1 * build(entity) >> type
        })
        codeRegistry.build()

        expect:
        typeManager.getQueryType(entity, GraphQLPropertyType.OUTPUT) == type
    }

    void "test getQueryType circular"() {
        given:
        PersistentEntity entity = One.gormPersistentEntity
        GraphQLObjectType type = GraphQLObjectType.newObject().name("One").field(GraphQLFieldDefinition.newFieldDefinition().name('foo').type(Scalars.GraphQLString).build()).build()
        GraphQLOutputType nestedType = null
        typeManager.objectTypeBuilders.put(GraphQLPropertyType.OUTPUT, Mock(AbstractObjectTypeBuilder) {
            1 * build(entity) >> {
                nestedType = typeManager.getQueryType(entity, GraphQLPropertyType.OUTPUT)
                type
            }
        })

        when:
        GraphQLOutputType returnedType = typeManager.getQueryType(entity, GraphQLPropertyType.OUTPUT)
        codeRegistry.build()

        then:
        returnedType == type
        nestedType instanceof GraphQLTypeReference
        notThrown(StackOverflowError)
    }

    void "test get mutation type"() {
        given:
        PersistentEntity entity = One.gormPersistentEntity
        GraphQLInputObjectType type = GraphQLInputObjectType.newInputObject().name('Test').field(GraphQLInputObjectField.newInputObjectField().name('foo').type(Scalars.GraphQLString)).build()
        typeManager.inputObjectTypeBuilders.put(GraphQLPropertyType.CREATE, Mock(AbstractInputObjectTypeBuilder) {
            2 * build(entity) >> type
        })
        GraphQLInputType mutationType = typeManager.getMutationType(entity, GraphQLPropertyType.CREATE, true)
        codeRegistry.build()

        expect:
        mutationType == type
        unwrap(null, typeManager.getMutationType(entity, GraphQLPropertyType.CREATE, false)) == type
    }

    @GraphQL('Foo type')
    enum Foo {

        @GraphQL(deprecated = true)
        X,

        @GraphQL(deprecationReason = 'Y is deprecated')
        Y,

        @GraphQL('This is the Z property')
        Z

    }

    enum Bar {
        A
    }
}
