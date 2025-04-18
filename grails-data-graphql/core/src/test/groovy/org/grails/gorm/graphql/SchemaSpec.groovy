package org.grails.gorm.graphql

import graphql.schema.*
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.gorm.graphql.domain.general.GeneralPackage
import org.grails.gorm.graphql.domain.hibernate.HibernatePackage
import org.grails.gorm.graphql.testing.GraphQLSchemaSpec
import org.grails.gorm.graphql.types.GraphQLOperationType
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SchemaSpec extends Specification implements GraphQLSchemaSpec {

    @Shared @AutoCleanup HibernateDatastore hibernateDatastore
    @Shared GraphQLSchema schema
    @Shared GraphQLObjectType queryType
    @Shared GraphQLObjectType mutationType

    void setupSpec() {
        hibernateDatastore = new HibernateDatastore(
                DatastoreUtils.createPropertyResolver(Collections.singletonMap(Settings.SETTING_DB_CREATE, 'create-drop')),
                GeneralPackage.getPackage(), HibernatePackage.getPackage())
        schema = new Schema(hibernateDatastore.mappingContext).generate()
        queryType = schema.queryType
        mutationType = schema.mutationType
    }

    void notNull(GraphQLType type, GraphQLType expectedType) {
        type instanceof GraphQLNonNull && ((GraphQLNonNull)type).wrappedType == expectedType
    }

    void list(GraphQLType type, GraphQLType expectedType) {
        type instanceof GraphQLList && ((GraphQLList)type).wrappedType == expectedType
    }

    private String normalizeType(GraphQLPropertyType type) {
        type.name().split('_').collect { String name ->
            name.toLowerCase().capitalize()
        }.join('').replace('Output', '')
    }

    private String normalizeType(String prefix, GraphQLPropertyType type) {
        prefix + normalizeType(type)
    }

    void "test ComplexOperation"() {
        given:
        GraphQLObjectType type = schema.getType('AwesomeType')
        GraphQLFieldDefinition query = queryType.getFieldDefinition('awesomeQuery')

        expect:
        type.getFieldDefinition('awesome')
        query.type == type
        query.getArgument('firstArg').type == schema.getType('FirstArgument')
    }

    void "test PaginatedOne"() {
        given:
        GraphQLFieldDefinition list = queryType.getFieldDefinition('paginatedOneList')

        expect: 'max and offset are required'
        list.type == schema.getType('PaginatedOnePagedResult')
    }

    void "test PaginatedTwo"() {
        given:
        GraphQLFieldDefinition list = queryType.getFieldDefinition('paginatedTwoList')

        expect: 'max and offset are required'
        list.type == schema.getType('PaginatedTwoPagedResult')
    }

    void "test SimpleOperation"() {
        given:
        GraphQLFieldDefinition query = queryType.getFieldDefinition('getData')
        GraphQLFieldDefinition query2 = queryType.getFieldDefinition('getMoreData')

        expect: 'max and offset are required'
        unwrap([], query.type) == schema.getType('OtherDomain')
        query2.type == schema.getType('OtherDomainPagedResult')
    }

    void "test FirstArgument"() {
        given:
        GraphQLInputObjectType type = schema.getType('FirstArgument')

        expect:
        type.getFieldDefinition('subArg')
        unwrap([null], type.getFieldDefinition('subArg2').type) == schema.getType('SubArgument2Input')
    }

    void "test SubArgument2"() {
        given:
        GraphQLInputObjectType type = schema.getType('SubArgument2Input')

        expect:
        type.getFieldDefinition('threeDeep')
    }

    void "test DebugBar"() {
        given:
        GraphQLObjectType type = schema.getType('DebugBar')

        expect:
        type.getFieldDefinition('foo').type == schema.getType('DebugFoo')
        type.getFieldDefinition('circular').type == schema.getType('DebugCircular')
    }

    void "test DebugFoo"() {
        given:
        GraphQLObjectType type = schema.getType('DebugFoo')

        expect:
        unwrap([], type.getFieldDefinition('items').type) == schema.getType('DebugFooItem')
        type.getFieldDefinition('bar').type == schema.getType('DebugBar')
    }

    void "test DebugFooItem"() {
        given:
        GraphQLObjectType type = schema.getType('DebugFooItem')

        expect:
        type.getFieldDefinition('foo').type == schema.getType('DebugFoo')
    }

    void "test DebugCircular"() {
        given:
        GraphQLObjectType type = schema.getType('DebugCircular')

        expect:
        type.getFieldDefinition('otherCircular').type == schema.getType('DebugCircular')
        unwrap([], type.getFieldDefinition('circulars').type) == schema.getType('DebugCircular')
        type.getFieldDefinition('parentCircular').type == schema.getType('DebugCircular')
    }

    void "test !ToOne"() {
        expect:
        schema.getType(normalizeType('ToOne', GraphQLPropertyType.CREATE_NESTED)) == null
        schema.getType(normalizeType('ToOne', GraphQLPropertyType.CREATE_EMBEDDED)) == null
        schema.getType(normalizeType('ToOne', GraphQLPropertyType.UPDATE_NESTED)) == null
        schema.getType(normalizeType('ToOne', GraphQLPropertyType.UPDATE_EMBEDDED)) == null
    }

    void "test ToOne"() {
        GraphQLObjectType type = schema.getType('ToOne')

        expect:
        type.getFieldDefinition('circularOne').type == schema.getType('CircularOne')
        type.getFieldDefinition('one').type == schema.getType('One')
        type.getFieldDefinition('anEnum').type == schema.getType('Enum')

        //everything else is a scalar.. not worth testing every property
    }

    void "test ToOneUpdate"() {
        GraphQLInputObjectType type = schema.getType('ToOneUpdate')

        expect:
        type.getFieldDefinition('circularOne').type == schema.getType('CircularOneUpdateNested')
        type.getFieldDefinition('one').type == schema.getType('OneUpdateNested')
        type.getFieldDefinition('anEnum').type == schema.getType('Enum')

        //everything else is a scalar.. not worth testing every property
    }

    void "test ToOneCreate"() {
        GraphQLInputObjectType type = schema.getType('ToOneCreate')

        expect:
        unwrap(null, type.getFieldDefinition('circularOne').type) == schema.getType('CircularOneCreateNested')
        unwrap(null, type.getFieldDefinition('one').type) == schema.getType('OneCreateNested')
        unwrap(null, type.getFieldDefinition('anEnum').type) == schema.getType('Enum')

        //everything else is a scalar.. not worth testing every property
    }

    void "test !One"() {
        expect:
        schema.getType(normalizeType('One', GraphQLPropertyType.CREATE)) == null
        schema.getType(normalizeType('One', GraphQLPropertyType.CREATE_EMBEDDED)) == null
        schema.getType(normalizeType('One', GraphQLPropertyType.UPDATE)) == null
        schema.getType(normalizeType('One', GraphQLPropertyType.UPDATE_EMBEDDED)) == null
    }

    void "test One"() {
        expect:
        schema.getType(normalizeType('One', GraphQLPropertyType.OUTPUT)) != null
        schema.getType(normalizeType('One', GraphQLPropertyType.CREATE_NESTED)) != null
        schema.getType(normalizeType('One', GraphQLPropertyType.UPDATE_NESTED)) != null
    }

    void "test CircularOneCreateNested"() {
        GraphQLInputObjectType nestedCircular = schema.getType('CircularOneCreateNested')

        expect:
        nestedCircular.getFieldDefinition('one').type == nestedCircular
    }

    void "test the default list arguments are applied"() {
        given:
        GraphQLFieldDefinition fooBar = queryType.getFieldDefinition("fooBar")

        expect:
        unwrap([], fooBar.type).name == "TestListArgs"
        fooBar.arguments.size() == 5
        fooBar.arguments[0].name == "max"
        fooBar.arguments[1].name == "offset"
        fooBar.arguments[2].name == "sort"
        fooBar.arguments[3].name == "order"
        fooBar.arguments[4].name == "ignoreCase"
    }

}