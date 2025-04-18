package grails.test.app

import grails.test.app.pogo.Painting
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import org.grails.gorm.graphql.binding.manager.GraphQLDataBinderManager
import org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType
import org.grails.gorm.graphql.interceptor.impl.BaseGraphQLFetcherInterceptor
import org.grails.gorm.graphql.interceptor.manager.GraphQLInterceptorManager
import org.grails.gorm.graphql.plugin.GraphQLPostProcessor
import grails.gorm.DetachedCriteria
import graphql.schema.DataFetchingEnvironment
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.fetcher.impl.EntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.SingleEntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.SoftDeleteEntityDataFetcher
import org.grails.gorm.graphql.fetcher.manager.GraphQLDataFetcherManager
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition


@CompileStatic
class GraphQLCustomizer extends GraphQLPostProcessor {

    @Override
    void doWith(GraphQLDataFetcherManager fetcherManager) {
        PersistentEntity entity = SoftDelete.gormPersistentEntity
        DeletingGormDataFetcher softDelete = new SoftDeleteEntityDataFetcher(entity, 'active', false)
        fetcherManager.registerDeletingDataFetcher(SoftDelete, softDelete)
        fetcherManager.registerReadingDataFetcher(SoftDelete, new SingleEntityDataFetcher(entity) {
            @Override
            protected DetachedCriteria buildCriteria(DataFetchingEnvironment environment) {
                super.buildCriteria(environment).where {
                    eq('active', true)
                }
            }
        })
        fetcherManager.registerReadingDataFetcher(SoftDelete, new EntityDataFetcher(entity) {
            @Override
            protected DetachedCriteria buildCriteria(DataFetchingEnvironment environment) {
                super.buildCriteria(environment).where {
                    eq('active', true)
                }
            }
        })
    }

    @Override
    void doWith(GraphQLInterceptorManager interceptorManager) {
        //The restricted domain cannot be edited or deleted
        interceptorManager.registerInterceptor(Restricted, new BaseGraphQLFetcherInterceptor() {
            boolean onMutation(DataFetchingEnvironment environment, GraphQLDataFetcherType type) {
                type == GraphQLDataFetcherType.CREATE
            }
        })
    }

    @Override
    void doWith(GraphQLDataBinderManager binderManager) {
        binderManager.registerDataBinder(User, new UserDataBinder())
        binderManager.registerDataBinder(Role, new RoleDataBinder())
    }

    @Override
    void doWith(GraphQLTypeManager typeManager) {
        GraphQLOutputType stringType = (GraphQLOutputType)typeManager.getType(String)
        GraphQLOutputType intType = (GraphQLOutputType)typeManager.getType(Integer)
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
                .name('Painting')
                .field(newFieldDefinition()
                        .name('name')
                        .type(stringType))
                .field(newFieldDefinition()
                        .name('artistName')
                        .type(stringType))
                .field(newFieldDefinition()
                        .name('heightCm')
                        .type(intType))
                .field(newFieldDefinition()
                        .name('widthCm')
                        .type(intType))


        typeManager.registerType(Painting, builder.build())
    }
}
