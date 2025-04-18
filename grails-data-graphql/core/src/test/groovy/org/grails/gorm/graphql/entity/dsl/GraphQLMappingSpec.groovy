package org.grails.gorm.graphql.entity.dsl

import graphql.Scalars
import graphql.scalars.ExtendedScalars
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInputObjectType
import graphql.schema.GraphQLList
import graphql.schema.GraphQLNonNull
import graphql.schema.GraphQLObjectType
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.GraphQLServiceManager
import org.grails.gorm.graphql.entity.GraphQLEntityNamingConvention
import org.grails.gorm.graphql.entity.property.impl.SimpleGraphQLProperty
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.fetcher.interceptor.InterceptingDataFetcher
import org.grails.gorm.graphql.interceptor.manager.DefaultGraphQLInterceptorManager
import org.grails.gorm.graphql.interceptor.manager.GraphQLInterceptorManager
import org.grails.gorm.graphql.response.pagination.DefaultGraphQLPaginationResponseHandler
import org.grails.gorm.graphql.testing.GraphQLSchemaSpec
import org.grails.gorm.graphql.types.DefaultGraphQLTypeManager
import org.grails.gorm.graphql.types.GraphQLTypeManager
import spock.lang.Specification

class GraphQLMappingSpec extends Specification implements GraphQLSchemaSpec {

    void "test exclude"() {
        given:
        GraphQLMapping mapping = GraphQLMapping.build {
            exclude('foo')
            exclude('bar')
        }

        expect:
        mapping.excluded.contains('foo')
        mapping.excluded.contains('bar')
    }

    void "test add"() {
        given:
        GraphQLMapping mapping = GraphQLMapping.build {
            add('fooBar', Integer) {
                description 'Foo Bar'
            }
            add('foo', String)
            add(new SimpleGraphQLProperty().name('barFoo').returns(Long))
            add('bar', String) {
                deprecationReason 'Deprecated'
            }
        }

        expect:
        mapping.additional.size() == 4
        mapping.additional[0].name == 'fooBar'
        ((SimpleGraphQLProperty)mapping.additional[0]).returnType == Integer
        mapping.additional[0].description == 'Foo Bar'
        mapping.additional[1].name == 'foo'
        ((SimpleGraphQLProperty)mapping.additional[1]).returnType == String
        mapping.additional[2].name == 'barFoo'
        ((SimpleGraphQLProperty)mapping.additional[2]).returnType == Long
        mapping.additional[3].name == 'bar'
        ((SimpleGraphQLProperty)mapping.additional[3]).returnType == String
        mapping.additional[3].deprecationReason == 'Deprecated'

        when:
        mapping.add(new SimpleGraphQLProperty())

        then:
        Exception ex = thrown(IllegalArgumentException)
        ex.message == 'A name is required for creating custom properties'
    }

    void "test modify existing property"() {
        when:
        GraphQLMapping mapping = GraphQLMapping.build {
            foo {
                description 'Foo'
            }
            bar deprecated: true
            fooBar GraphQLPropertyMapping.build {
                dataFetcher {

                }
            }
        }

        then:
        mapping.propertyMappings.size() == 3
        mapping.propertyMappings.get('foo').description == 'Foo'
        mapping.propertyMappings.get('bar').deprecated
        mapping.propertyMappings.get('fooBar').dataFetcher != null
    }

    void "test modify existing property with property method"() {
        when:
        GraphQLMapping mapping = GraphQLMapping.build {
            property('foo') {
                description 'Foo'
            }
            property('bar', [deprecated: true])
            property('fooBar', GraphQLPropertyMapping.build {
                dataFetcher {

                }
            })
        }

        then:
        mapping.propertyMappings.size() == 3
        mapping.propertyMappings.get('foo').description == 'Foo'
        mapping.propertyMappings.get('bar').deprecated
        mapping.propertyMappings.get('fooBar').dataFetcher != null
    }

    void "test adding an operation" () {
        given:
        GraphQLCodeRegistry.Builder codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        GraphQLTypeManager typeManager = new DefaultGraphQLTypeManager(codeRegistry, new GraphQLEntityNamingConvention(), null, new DefaultGraphQLDomainPropertyManager(), new DefaultGraphQLPaginationResponseHandler())
        GraphQLServiceManager serviceManager = new GraphQLServiceManager()
        GraphQLInterceptorManager interceptorManager = new DefaultGraphQLInterceptorManager()
        PersistentEntity entity = Stub(PersistentEntity) {
            getJavaClass() >> GraphQLMappingSpec
        }
        serviceManager.registerService(GraphQLTypeManager, typeManager)
        serviceManager.registerService(GraphQLInterceptorManager, interceptorManager)

        DataFetcher defaultFetcher = new DataFetcher() {
            @Override
            Object get(DataFetchingEnvironment environment) {
                return null
            }
        }

        GraphQLMapping mapping = GraphQLMapping.build {
            query('foo', BigDecimal) {
                argument('bar', String) {
                    description('Bar argument')
                    defaultValue 'b'
                }
                dataFetcher(defaultFetcher)
                description('Foo Query')
                deprecationReason('Foo Query is deprecated')
            }

            query('bar', [BigDecimal]) {
                argument('foo', [String])
                dataFetcher(defaultFetcher)
                description('Bar Query')
                deprecated(true)
            }

            mutation('xyz', 'BarObject') {
                returns {
                    field('bar', String)
                }
                argument('fooBar', 'FooArgument') {
                    accepts {
                        field('foo', Integer)
                    }
                }
                dataFetcher(defaultFetcher)
                description('ZYX mutation')
            }
        }

        when:
        GraphQLFieldDefinition foo = mapping.customQueryOperations.find { it.name == 'foo' }.createField(entity, serviceManager, null, [:]).build()
        GraphQLFieldDefinition bar = mapping.customQueryOperations.find { it.name == 'bar' }.createField(entity, serviceManager, null, [:]).build()
        GraphQLFieldDefinition xyz = mapping.customMutationOperations.find { it.name == 'xyz' }.createField(entity, serviceManager, null, [:]).build()

        and:
        codeRegistry.build()

        then:
        foo.description == 'Foo Query'
        foo.deprecated
        foo.deprecationReason == 'Foo Query is deprecated'
        foo.type == ExtendedScalars.GraphQLBigDecimal
        foo.arguments.size() == 1
        unwrap(null, foo.getArgument('bar').type) == Scalars.GraphQLString
        foo.getArgument('bar').description == 'Bar argument'
        foo.getArgument('bar').argumentDefaultValue.value == 'b'

        bar.description == 'Bar Query'
        bar.deprecated
        bar.deprecationReason == 'Deprecated'
        bar.type instanceof GraphQLList
        unwrap([], bar.type) == ExtendedScalars.GraphQLBigDecimal
        bar.arguments.size() == 1
        bar.getArgument('foo').type instanceof GraphQLList
        bar.getArgument('foo').description == null
        bar.getArgument('foo').argumentDefaultValue.value == null
        unwrap([null], bar.getArgument('foo').type) == Scalars.GraphQLString

        xyz.description == 'ZYX mutation'
        !xyz.deprecated
        xyz.type instanceof GraphQLObjectType
        ((GraphQLObjectType)xyz.type).fieldDefinitions[0].name == 'bar'
        ((GraphQLObjectType)xyz.type).fieldDefinitions[0].type == Scalars.GraphQLString
        ((GraphQLObjectType)xyz.type).fieldDefinitions.size() == 1
        xyz.arguments.size() == 1
        xyz.arguments[0].type instanceof GraphQLNonNull
        unwrap(null, xyz.arguments[0].type) instanceof GraphQLInputObjectType
        xyz.arguments[0].name == 'fooBar'

        ((GraphQLInputObjectType)((GraphQLNonNull)xyz.arguments[0].type).wrappedType).getField('foo').type instanceof GraphQLNonNull
        ((GraphQLNonNull)(((GraphQLInputObjectType)((GraphQLNonNull)xyz.arguments[0].type).wrappedType)).getField('foo').type).wrappedType == Scalars.GraphQLInt
        (((GraphQLInputObjectType)((GraphQLNonNull)xyz.arguments[0].type).wrappedType)).fieldDefinitions.size() == 1
    }
}
