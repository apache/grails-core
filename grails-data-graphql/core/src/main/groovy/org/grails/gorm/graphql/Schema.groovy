package org.grails.gorm.graphql

import graphql.schema.*
import groovy.transform.CompileStatic
import javassist.Modifier
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.gorm.graphql.binding.DataBinderNotFoundException
import org.grails.gorm.graphql.binding.GraphQLDataBinder
import org.grails.gorm.graphql.binding.manager.DefaultGraphQLDataBinderManager
import org.grails.gorm.graphql.binding.manager.GraphQLDataBinderManager
import org.grails.gorm.graphql.entity.GraphQLEntityNamingConvention
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.entity.operations.CustomOperation
import org.grails.gorm.graphql.entity.operations.ListOperation
import org.grails.gorm.graphql.entity.operations.ProvidedOperation
import org.grails.gorm.graphql.entity.property.manager.DefaultGraphQLDomainPropertyManager
import org.grails.gorm.graphql.entity.property.manager.GraphQLDomainPropertyManager
import org.grails.gorm.graphql.fetcher.BindingGormDataFetcher
import org.grails.gorm.graphql.fetcher.DeletingGormDataFetcher
import org.grails.gorm.graphql.fetcher.PaginatingGormDataFetcher
import org.grails.gorm.graphql.fetcher.impl.CountEntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.CreateEntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.DeleteEntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.EntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.PaginatedEntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.SingleEntityDataFetcher
import org.grails.gorm.graphql.fetcher.impl.UpdateEntityDataFetcher
import org.grails.gorm.graphql.fetcher.interceptor.InterceptingDataFetcher
import org.grails.gorm.graphql.fetcher.interceptor.InterceptorInvoker
import org.grails.gorm.graphql.fetcher.interceptor.MutationInterceptorInvoker
import org.grails.gorm.graphql.fetcher.interceptor.QueryInterceptorInvoker
import org.grails.gorm.graphql.fetcher.manager.DefaultGraphQLDataFetcherManager
import org.grails.gorm.graphql.fetcher.manager.GraphQLDataFetcherManager
import org.grails.gorm.graphql.interceptor.GraphQLSchemaInterceptor
import org.grails.gorm.graphql.interceptor.manager.DefaultGraphQLInterceptorManager
import org.grails.gorm.graphql.interceptor.manager.GraphQLInterceptorManager
import org.grails.gorm.graphql.response.delete.DefaultGraphQLDeleteResponseHandler
import org.grails.gorm.graphql.response.delete.GraphQLDeleteResponseHandler
import org.grails.gorm.graphql.response.errors.DefaultGraphQLErrorsResponseHandler
import org.grails.gorm.graphql.response.errors.GraphQLErrorsResponseHandler
import org.grails.gorm.graphql.response.pagination.DefaultGraphQLPaginationResponseHandler
import org.grails.gorm.graphql.response.pagination.GraphQLPaginationResponseHandler
import org.grails.gorm.graphql.types.DefaultGraphQLTypeManager
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager
import org.grails.gorm.graphql.types.scalars.coercing.DateCoercion
import org.grails.gorm.graphql.types.scalars.coercing.jsr310.*
import org.springframework.context.support.StaticMessageSource
import javax.annotation.PostConstruct
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZonedDateTime

import static graphql.schema.FieldCoordinates.coordinates
import static graphql.schema.GraphQLArgument.newArgument
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLList.list
import static graphql.schema.GraphQLObjectType.newObject
import static graphql.schema.GraphQLScalarType.newScalar
import static org.grails.gorm.graphql.fetcher.GraphQLDataFetcherType.*

/**
 * Created by jameskleeh on 5/19/17.
 */
@CompileStatic
class Schema {

    public static final String DEFAULT_DEPRECATION_REASON = 'Deprecated'

    protected MappingContext[] mappingContexts

    GraphQLCodeRegistry.Builder codeRegistry
    GraphQLTypeManager typeManager
    GraphQLDeleteResponseHandler deleteResponseHandler
    GraphQLEntityNamingConvention namingConvention
    GraphQLDataBinderManager dataBinderManager
    GraphQLDataFetcherManager dataFetcherManager
    GraphQLInterceptorManager interceptorManager
    GraphQLErrorsResponseHandler errorsResponseHandler
    GraphQLDomainPropertyManager domainPropertyManager
    GraphQLPaginationResponseHandler paginationResponseHandler
    GraphQLServiceManager serviceManager

    Map<String, GraphQLInputType> listArguments

    List<String> dateFormats
    boolean dateFormatLenient = false

    private boolean initialized = false

    Schema(MappingContext... mappingContext) {
        this.mappingContexts = mappingContext
    }

    void setListArguments(Map<String, Class> arguments) {
        listArguments = buildListArguments(arguments)
    }

    Map<String, GraphQLInputType> buildListArguments(Map<String, Class> arguments) {
        if (arguments != null) {
            Map<String, GraphQLInputType> listArguments = [:]
            for (Map.Entry<String, Class> entry : arguments) {
                GraphQLType type = typeManager.getType(entry.value)
                if (!(type instanceof GraphQLInputType)) {
                    throw new IllegalArgumentException("Error while setting list arguments. Invalid returnType found for ${entry.value.name}. GraphQLType found ${type} of returnType ${type.class.name} is not an instance of ${GraphQLInputType.name}")
                }
                listArguments.put(entry.key, (GraphQLInputType) type)
            }
            return listArguments
        }
    }

    void populateDefaultDateTypes() {
        if (!typeManager.hasType(Date)) {
            typeManager.registerType(Date, newScalar().name('Date').description('Built-in Date').coercing(new DateCoercion(dateFormats, dateFormatLenient)).build())
        }
        if (!typeManager.hasType(Instant)) {
            typeManager.registerType(Instant, newScalar().name('Instant').description('Built-in Instant').coercing(new InstantCoercion()).build())
        }
        if (!typeManager.hasType(LocalDate)) {
            typeManager.registerType(LocalDate, newScalar().name('LocalDate').description('Built-in LocalDate').coercing(new LocalDateCoercion(dateFormats)).build())
        }
        if (!typeManager.hasType(LocalDateTime)) {
            typeManager.registerType(LocalDateTime, newScalar().name('LocalDateTime').description('Built-in LocalDateTime').coercing(new LocalDateTimeCoercion(dateFormats)).build())
        }
        if (!typeManager.hasType(LocalTime)) {
            typeManager.registerType(LocalTime, newScalar().name('LocalTime').description('Built-in LocalTime').coercing(new LocalTimeCoercion(dateFormats)).build())
        }
        if (!typeManager.hasType(OffsetDateTime)) {
            typeManager.registerType(OffsetDateTime, newScalar().name('OffsetDateTime').description('Built-in OffsetDateTime').coercing(new OffsetDateTimeCoercion(dateFormats)).build())
        }
        if (!typeManager.hasType(OffsetTime)) {
            typeManager.registerType(OffsetTime, newScalar().name('OffsetTime').description('Built-in OffsetTime').coercing(new OffsetTimeCoercion(dateFormats)).build())
        }
        if (!typeManager.hasType(ZonedDateTime)) {
            typeManager.registerType(ZonedDateTime, newScalar().name('ZonedDateTime').description('Built-in ZonedDateTime').coercing(new ZonedDateTimeCoercion(dateFormats)).build())
        }
    }

    @PostConstruct
    void initialize() {
        if (codeRegistry == null) {
            codeRegistry = GraphQLCodeRegistry.newCodeRegistry()
        }
        if (namingConvention == null) {
            namingConvention = new GraphQLEntityNamingConvention()
        }
        if (errorsResponseHandler == null) {
            errorsResponseHandler = new DefaultGraphQLErrorsResponseHandler(new StaticMessageSource(), codeRegistry)
        }
        if (domainPropertyManager == null) {
            domainPropertyManager = new DefaultGraphQLDomainPropertyManager()
        }
        if (paginationResponseHandler == null) {
            paginationResponseHandler = new DefaultGraphQLPaginationResponseHandler()
        }

        if (typeManager == null) {
            typeManager = new DefaultGraphQLTypeManager(codeRegistry, namingConvention, errorsResponseHandler, domainPropertyManager, paginationResponseHandler)
        }

        populateDefaultDateTypes()

        if (deleteResponseHandler == null) {
            deleteResponseHandler = new DefaultGraphQLDeleteResponseHandler()
        }
        if (dataBinderManager == null) {
            dataBinderManager = new DefaultGraphQLDataBinderManager()
        }
        if (dataFetcherManager == null) {
            dataFetcherManager = new DefaultGraphQLDataFetcherManager()
        }
        if (listArguments == null) {
            setListArguments(EntityDataFetcher.ARGUMENTS)
        }
        if (interceptorManager == null) {
            interceptorManager = new DefaultGraphQLInterceptorManager()
        }
        if (serviceManager == null) {
            serviceManager = new GraphQLServiceManager()
        }

        serviceManager.with {
            registerService(GraphQLTypeManager, typeManager)
            registerService(GraphQLEntityNamingConvention, namingConvention)
            registerService(GraphQLDeleteResponseHandler, deleteResponseHandler)
            registerService(GraphQLDataBinderManager, dataBinderManager)
            registerService(GraphQLDataFetcherManager, dataFetcherManager)
            registerService(GraphQLInterceptorManager, interceptorManager)
            registerService(GraphQLDomainPropertyManager, domainPropertyManager)
            registerService(GraphQLErrorsResponseHandler, errorsResponseHandler)
            registerService(GraphQLPaginationResponseHandler, paginationResponseHandler)
        }

        initialized = true
    }

    protected void populateIdentityArguments(PersistentEntity entity, GraphQLFieldDefinition.Builder... builders) {
        Map<String, Class> identities = [:]

        if (entity.identity != null) {
            identities.put(entity.identity.name, entity.identity.type)
        } else if (entity.compositeIdentity != null) {
            for (PersistentProperty identity : entity.compositeIdentity) {
                if (identity instanceof Association) {
                    PersistentEntity associatedEntity = ((Association) identity).associatedEntity
                    if (associatedEntity.identity != null) {
                        identities.put(identity.name, associatedEntity.identity.type)
                    } else {
                        throw new UnsupportedOperationException("Mapping domain classes with nested composite keys is not currently supported. ${identity} has a composite key.")
                    }
                } else {
                    identities.put(identity.name, identity.type)
                }
            }
        }

        for (Map.Entry<String, Class> identity : identities) {
            GraphQLInputType inputType = (GraphQLInputType) typeManager.getType(identity.value, false)

            for (GraphQLFieldDefinition.Builder builder : builders) {
                builder.argument(newArgument()
                        .name(identity.key)
                        .type(inputType))
            }
        }
    }

    GraphQLSchema generate() {

        if (!initialized) {
            initialize()
        }
        final String queryTypeName = 'Query'
        final String mutationTypeName = 'Mutation'

        GraphQLObjectType.Builder queryType = newObject().name(queryTypeName)
        GraphQLObjectType.Builder mutationType = newObject().name(mutationTypeName)

        Set<PersistentEntity> childrenNotMapped = []

        for (MappingContext mappingContext : mappingContexts) {
            for (PersistentEntity entity : mappingContext.persistentEntities) {

                GraphQLMapping mapping = GraphQLEntityHelper.getMapping(entity)
                if (mapping == null) {
                    if (!entity.root) {
                        childrenNotMapped.add(entity)
                    }
                    continue
                } else if (!mapping.operations.all.enabled) {
                    continue
                }

                List<GraphQLFieldDefinition.Builder> queryFields = []
                List<GraphQLFieldDefinition.Builder> mutationFields = []

                final GraphQLOutputType objectType = typeManager.getQueryType(entity, GraphQLPropertyType.OUTPUT)

                List<GraphQLFieldDefinition.Builder> requiresIdentityArguments = []
                List<Closure> postIdentityExecutables = []
                InterceptorInvoker queryInterceptorInvoker = new QueryInterceptorInvoker()

                ProvidedOperation queryOperation = mapping.operations.query
                ProvidedOperation mutationOperation = mapping.operations.mutation

                ProvidedOperation getOperation = mapping.operations.get
                if (queryOperation.enabled && getOperation.enabled) {

                    DataFetcher getFetcher = dataFetcherManager.getReadingFetcher(entity, GET).orElse(new SingleEntityDataFetcher(entity))

                    final String getFieldName = namingConvention.getGet(entity)

                    GraphQLFieldDefinition.Builder queryOne = newFieldDefinition()
                            .name(getFieldName)
                            .type(objectType)
                            .description(getOperation.description)
                            .deprecate(getOperation.deprecationReason)

                    codeRegistry
                            .dataFetcher(
                                    coordinates(queryTypeName, getFieldName),
                                    new InterceptingDataFetcher(entity, serviceManager, queryInterceptorInvoker, GET, getFetcher)
                            )

                    requiresIdentityArguments.add(queryOne)
                    queryFields.add(queryOne)
                }

                ListOperation listOperation = mapping.operations.list
                if (queryOperation.enabled && listOperation.enabled) {

                    DataFetcher listFetcher = dataFetcherManager.getReadingFetcher(entity, LIST).orElse(null)

                    final String listFieldName = namingConvention.getList(entity)
                    GraphQLFieldDefinition.Builder queryAll = newFieldDefinition()
                            .name(listFieldName)
                            .description(listOperation.description)
                            .deprecate(listOperation.deprecationReason)

                    GraphQLOutputType listOutputType
                    if (listOperation.paginate) {
                        if (listFetcher == null) {
                            listFetcher = new PaginatedEntityDataFetcher(entity)
                        }
                        listOutputType = typeManager.getQueryType(entity, GraphQLPropertyType.OUTPUT_PAGED)
                    } else {
                        if (listFetcher == null) {
                            listFetcher = new EntityDataFetcher(entity)
                        }
                        listOutputType = list(objectType)
                    }
                    queryAll.type(listOutputType)

                    if (listFetcher instanceof PaginatingGormDataFetcher) {
                        ((PaginatingGormDataFetcher) listFetcher).responseHandler = paginationResponseHandler
                    }

                    codeRegistry.dataFetcher(
                            coordinates(queryTypeName, listFieldName),
                            new InterceptingDataFetcher(entity, serviceManager, queryInterceptorInvoker, LIST, listFetcher)
                    )

                    queryFields.add(queryAll)

                    for (Map.Entry<String, GraphQLInputType> argument : listArguments) {
                        queryAll.argument(
                                newArgument()
                                        .name(argument.key)
                                        .type(argument.value))
                    }
                }

                ProvidedOperation countOperation = mapping.operations.count
                if (queryOperation.enabled && countOperation.enabled) {

                    DataFetcher countFetcher = dataFetcherManager.getReadingFetcher(entity, COUNT).orElse(new CountEntityDataFetcher(entity))

                    final String countFieldName = namingConvention.getCount(entity)
                    final GraphQLOutputType countOutputType = (GraphQLOutputType) typeManager.getType(Integer)

                    GraphQLFieldDefinition.Builder queryCount = newFieldDefinition()
                            .name(countFieldName)
                            .type(countOutputType)
                            .description(countOperation.description)
                            .deprecate(countOperation.deprecationReason)

                    codeRegistry.dataFetcher(
                            coordinates(queryTypeName, countFieldName),
                            new InterceptingDataFetcher(entity, serviceManager, queryInterceptorInvoker, COUNT, countFetcher)
                    )

                    queryFields.add(queryCount)
                }

                InterceptorInvoker mutationInterceptorInvoker = new MutationInterceptorInvoker()

                GraphQLDataBinder dataBinder = dataBinderManager.getDataBinder(entity.javaClass)

                ProvidedOperation createOperation = mapping.operations.create
                if (mutationOperation.enabled && createOperation.enabled && !Modifier.isAbstract(entity.javaClass.modifiers)) {
                    if (dataBinder == null) {
                        throw new DataBinderNotFoundException(entity)
                    }
                    GraphQLInputType createObjectType = typeManager.getMutationType(entity, GraphQLPropertyType.CREATE, true)

                    if (!createObjectType.children.empty) {
                        BindingGormDataFetcher createFetcher = dataFetcherManager.getBindingFetcher(entity, CREATE).orElse(new CreateEntityDataFetcher(entity))
                        createFetcher.dataBinder = dataBinder

                        final String createFieldName = namingConvention.getCreate(entity)

                        GraphQLFieldDefinition.Builder create = newFieldDefinition()
                                .name(createFieldName)
                                .type(objectType)
                                .description(createOperation.description)
                                .deprecate(createOperation.deprecationReason)
                                .argument(newArgument()
                                        .name(entity.decapitalizedName)
                                        .type(createObjectType))

                        codeRegistry.dataFetcher(
                                coordinates(mutationTypeName, createFieldName),
                                new InterceptingDataFetcher(entity, serviceManager, mutationInterceptorInvoker, CREATE, createFetcher)
                        )

                        mutationFields.add(create)
                    }
                }

                ProvidedOperation updateOperation = mapping.operations.update
                if (mutationOperation.enabled && updateOperation.enabled) {
                    if (dataBinder == null) {
                        throw new DataBinderNotFoundException(entity)
                    }
                    GraphQLInputType updateObjectType = typeManager.getMutationType(entity, GraphQLPropertyType.UPDATE, true)

                    BindingGormDataFetcher updateFetcher = dataFetcherManager.getBindingFetcher(entity, UPDATE).orElse(new UpdateEntityDataFetcher(entity))

                    updateFetcher.dataBinder = dataBinder

                    final String updateFieldName = namingConvention.getUpdate(entity)

                    GraphQLFieldDefinition.Builder update = newFieldDefinition()
                            .name(updateFieldName)
                            .type(objectType)
                            .description(updateOperation.description)
                            .deprecate(updateOperation.deprecationReason)

                    codeRegistry.dataFetcher(
                            coordinates(mutationTypeName, updateFieldName),
                            new InterceptingDataFetcher(entity, serviceManager, mutationInterceptorInvoker, UPDATE, updateFetcher)
                    )

                    postIdentityExecutables.add {
                        update.argument(newArgument()
                                .name(entity.decapitalizedName)
                                .type(updateObjectType))
                    }

                    requiresIdentityArguments.add(update)
                    mutationFields.add(update)
                }

                ProvidedOperation deleteOperation = mapping.operations.delete
                if (mutationOperation.enabled && deleteOperation.enabled) {

                    DeletingGormDataFetcher deleteFetcher = dataFetcherManager.getDeletingFetcher(entity).orElse(new DeleteEntityDataFetcher(entity))

                    deleteFetcher.responseHandler = deleteResponseHandler

                    final String deleteFieldName = namingConvention.getDelete(entity)
                    final GraphQLObjectType deleteObjectType = deleteResponseHandler.getObjectType(typeManager)

                    GraphQLFieldDefinition.Builder delete = newFieldDefinition()
                            .name(deleteFieldName)
                            .type(deleteObjectType)
                            .description(deleteOperation.description)
                            .deprecate(deleteOperation.deprecationReason)

                    codeRegistry.dataFetcher(
                            coordinates(mutationTypeName, deleteFieldName),
                            new InterceptingDataFetcher(entity, serviceManager, mutationInterceptorInvoker, DELETE, deleteFetcher)
                    )

                    requiresIdentityArguments.add(delete)
                    mutationFields.add(delete)
                }

                final GraphQLFieldDefinition.Builder[] builders = requiresIdentityArguments as GraphQLFieldDefinition.Builder[]
                populateIdentityArguments(entity, builders)

                for (Closure c : postIdentityExecutables) {
                    c.call()
                }

                for (CustomOperation operation : mapping.customQueryOperations) {
                    queryFields.add(operation.createField(entity, serviceManager, mappingContext, listArguments))
                }

                for (CustomOperation operation : mapping.customMutationOperations) {
                    mutationFields.add(operation.createField(entity, serviceManager, mappingContext, Collections.emptyMap()))
                }

                for (GraphQLSchemaInterceptor schemaInterceptor : interceptorManager.interceptors) {
                    schemaInterceptor.interceptEntity(entity, queryFields, mutationFields)
                }

                queryType.fields((List<GraphQLFieldDefinition>) queryFields*.build())

                mutationType.fields((List<GraphQLFieldDefinition>) mutationFields*.build())
            }
        }

        Set<GraphQLType> additionalTypes = []

        for (PersistentEntity entity : childrenNotMapped) {
            GraphQLMapping mapping = GraphQLEntityHelper.getMapping(entity.rootEntity)
            if (mapping == null) {
                continue
            }

            additionalTypes.add(typeManager.getQueryType(entity, GraphQLPropertyType.OUTPUT))
        }

        for (GraphQLSchemaInterceptor schemaInterceptor : interceptorManager.interceptors) {
            schemaInterceptor.interceptSchema(queryType, mutationType, additionalTypes)
        }

        GraphQLSchema.Builder schema = GraphQLSchema.newSchema()
                .codeRegistry(codeRegistry.build())
                .additionalTypes(additionalTypes)

        GraphQLObjectType mutation = mutationType.build()
        if (mutation.fieldDefinitions) {
            schema.mutation(mutation)
        }
        GraphQLObjectType query = queryType.build()
        if (query.fieldDefinitions) {
            schema.query(query)
            return schema.build()
        }
    }

}
