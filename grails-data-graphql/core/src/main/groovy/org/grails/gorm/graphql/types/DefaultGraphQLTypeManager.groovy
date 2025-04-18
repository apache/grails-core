package org.grails.gorm.graphql.types

import graphql.Scalars
import graphql.scalars.ExtendedScalars
import graphql.schema.*
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.reflect.ClassUtils
import org.grails.gorm.graphql.GraphQL
import org.grails.gorm.graphql.Schema
import org.grails.gorm.graphql.entity.GraphQLEntityNamingConvention
import org.grails.gorm.graphql.entity.property.manager.GraphQLDomainPropertyManager
import org.grails.gorm.graphql.response.errors.GraphQLErrorsResponseHandler
import org.grails.gorm.graphql.response.pagination.GraphQLPaginationResponseHandler
import org.grails.gorm.graphql.types.input.*
import org.grails.gorm.graphql.types.output.EmbeddedObjectTypeBuilder
import org.grails.gorm.graphql.types.output.ObjectTypeBuilder
import org.grails.gorm.graphql.types.output.PaginatedObjectTypeBuilder
import org.grails.gorm.graphql.types.output.ShowObjectTypeBuilder
import org.grails.gorm.graphql.types.scalars.CustomScalars

import java.lang.reflect.Array
import java.sql.Time
import java.sql.Timestamp
import java.util.concurrent.ConcurrentHashMap

/**
 * The default implementation of {@link GraphQLTypeManager}
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLTypeManager implements GraphQLTypeManager {

    private Map<GraphQLPropertyType, List<PersistentEntity>> entitiesInProgress = [:].withDefault { [] }

    protected static final Map<Class, GraphQLType> TYPE_MAP = new ConcurrentHashMap<Class, GraphQLType>([
        (Integer): Scalars.GraphQLInt,
        (Long):  ExtendedScalars.GraphQLLong,
        (Short): ExtendedScalars.GraphQLShort,
        (Byte): ExtendedScalars.GraphQLByte,
        (Byte[]): CustomScalars.GraphQLByteArray,
        (Double): Scalars.GraphQLFloat,
        (Float): Scalars.GraphQLFloat,
        (BigInteger): ExtendedScalars.GraphQLBigInteger,
        (BigDecimal): ExtendedScalars.GraphQLBigDecimal,
        (String): Scalars.GraphQLString,
        (Boolean): Scalars.GraphQLBoolean,
        (Character): ExtendedScalars.GraphQLChar,
        (Character[]): CustomScalars.GraphQLCharacterArray,
        (UUID): CustomScalars.GraphQLUUID,
        (URL): CustomScalars.GraphQLURL,
        (URI): CustomScalars.GraphQLURI,
        (Time): CustomScalars.GraphQLTime,
        (java.sql.Date): CustomScalars.GraphQLSqlDate,
        (Timestamp): CustomScalars.GraphQLTimestamp,
        (Currency): CustomScalars.GraphQLCurrency,
        (TimeZone): CustomScalars.GraphQLTimeZone
    ])

    protected static final Map<Class, GraphQLEnumType> ENUM_TYPES = new ConcurrentHashMap<>()

    final GraphQLCodeRegistry.Builder codeRegistry
    GraphQLEntityNamingConvention namingConvention
    GraphQLErrorsResponseHandler errorsResponseHandler
    GraphQLDomainPropertyManager propertyManager
    GraphQLPaginationResponseHandler paginationResponseHandler

    Map<GraphQLPropertyType, InputObjectTypeBuilder> inputObjectTypeBuilders = [:]
    Map<GraphQLPropertyType, ObjectTypeBuilder> objectTypeBuilders = [:]

    DefaultGraphQLTypeManager(GraphQLCodeRegistry.Builder codeRegistry,
                              GraphQLEntityNamingConvention namingConvention,
                              GraphQLErrorsResponseHandler errorsResponseHandler,
                              GraphQLDomainPropertyManager propertyManager,
                              GraphQLPaginationResponseHandler paginationResponseHandler) {
        this.codeRegistry = codeRegistry
        this.namingConvention = namingConvention
        this.propertyManager = propertyManager
        this.errorsResponseHandler = errorsResponseHandler
        this.paginationResponseHandler = paginationResponseHandler
        initialize()
    }

    void initialize() {
        List<InputObjectTypeBuilder> inputBuilders = []
        GraphQLTypeManager typeManager = this
        inputBuilders.with {
            add(new CreateInputObjectTypeBuilder(propertyManager, typeManager))
            add(new NestedInputObjectTypeBuilder(propertyManager, typeManager, GraphQLPropertyType.CREATE_NESTED))
            add(new NestedInputObjectTypeBuilder(propertyManager, typeManager, GraphQLPropertyType.UPDATE_NESTED))
            add(new UpdateInputObjectTypeBuilder(propertyManager, typeManager))
            add(new EmbeddedInputObjectTypeBuilder(propertyManager, typeManager, GraphQLPropertyType.UPDATE_EMBEDDED))
            add(new EmbeddedInputObjectTypeBuilder(propertyManager, typeManager, GraphQLPropertyType.CREATE_EMBEDDED))
        }

        for (InputObjectTypeBuilder builder: inputBuilders) {
            inputObjectTypeBuilders.put(builder.type, builder)
        }

        List<ObjectTypeBuilder> builders = []
        builders.add(new EmbeddedObjectTypeBuilder(codeRegistry, propertyManager, typeManager, null))
        builders.add(new ShowObjectTypeBuilder(codeRegistry, propertyManager, typeManager, errorsResponseHandler))
        builders.add(new PaginatedObjectTypeBuilder(paginationResponseHandler, typeManager))

        for (ObjectTypeBuilder builder: builders) {
            objectTypeBuilders.put(builder.type, builder)
        }
    }

    private Class unwrap(Class clazz) {
        if (clazz.array) {
            if (clazz.componentType.primitive) {
                clazz = Array.newInstance(boxPrimitive(clazz.componentType), 0).getClass()
            }
        }
        else if (clazz.primitive) {
            clazz = boxPrimitive(clazz)
        }
        clazz
    }

    @Override
    GraphQLType getType(Class clazz, boolean nullable = true) {
        clazz = unwrap(clazz)

        GraphQLType type = TYPE_MAP.get(clazz)
        if (type == null) {
            throw new TypeNotFoundException(clazz)
        }
        if (nullable) {
            type
        }
        else {
            GraphQLNonNull.nonNull(type)
        }
    }

    @Override
    boolean hasType(Class clazz) {
        TYPE_MAP.containsKey(unwrap(clazz))
    }

    protected Class boxPrimitive(Class clazz) {
        ClassUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(clazz)
    }

    @Override
    void registerType(Class clazz, GraphQLType type) {
        TYPE_MAP.put(clazz, type)
    }

    @Override
    GraphQLType getEnumType(Class<? extends Enum> clazz, boolean nullable) {
        GraphQLEnumType enumType

        if (ENUM_TYPES.containsKey(clazz)) {
            enumType = ENUM_TYPES.get(clazz)
        }
        else {
            GraphQLEnumType.Builder builder = GraphQLEnumType.newEnum()
                    .name(clazz.simpleName)

            GraphQL annotation = clazz.getAnnotation(GraphQL)

            if (annotation != null && !annotation.value().empty) {
                builder.description(annotation.value())
            }

            for (Enum anEnum: clazz.enumConstants) {
                final String name = anEnum.name()

                String description = null
                String deprecationReason = null

                GraphQL valueAnnotation = clazz.getField(name).getAnnotation(GraphQL)
                if (valueAnnotation != null) {
                    if (!valueAnnotation.deprecationReason().empty) {
                        deprecationReason = valueAnnotation.deprecationReason()
                    }
                    else if (valueAnnotation.deprecated()) {
                        deprecationReason = Schema.DEFAULT_DEPRECATION_REASON
                    }
                    if (!valueAnnotation.value().empty) {
                        description = valueAnnotation.value()
                    }
                }

                builder.value(name, anEnum, description, deprecationReason)
            }

            enumType = builder.build()
            ENUM_TYPES.put(clazz, enumType)
        }

        if (nullable) {
            enumType
        }
        else {
            GraphQLNonNull.nonNull(enumType)
        }
    }

    @Override
    GraphQLTypeReference createReference(PersistentEntity entity, GraphQLPropertyType type) {
        new GraphQLTypeReference(namingConvention.getType(entity, type))
    }

    @Override
    GraphQLOutputType getQueryType(PersistentEntity entity, GraphQLPropertyType type) {
        if (objectTypeBuilders.containsKey(type)) {
            List<PersistentEntity> entitiesInProgress = entitiesInProgress.get(type)
            if (entitiesInProgress.contains(entity)) {
                (GraphQLOutputType)createReference(entity, type)
            }
            else {
                entitiesInProgress.add(entity)
                GraphQLOutputType outputType = objectTypeBuilders.get(type).build(entity)
                entitiesInProgress.removeElement(entity)
                outputType
            }
        }
        else {
            throw new IllegalArgumentException("Invalid type specified. ${type.name()} is not a valid query type")
        }
    }

    @Override
    GraphQLInputType getMutationType(PersistentEntity entity, GraphQLPropertyType type, boolean nullable) {
        if (inputObjectTypeBuilders.containsKey(type)) {
            GraphQLInputType inputType
            List<PersistentEntity> entitiesInProgress = entitiesInProgress.get(type)
            if (entitiesInProgress.contains(entity)) {
                inputType = (GraphQLInputType)createReference(entity, type)
            }
            else {
                entitiesInProgress.add(entity)
                inputType = inputObjectTypeBuilders.get(type).build(entity)
                entitiesInProgress.removeElement(entity)
            }

            if (nullable) {
                inputType
            }
            else {
                GraphQLNonNull.nonNull(inputType)
            }
        }
        else {
            throw new IllegalArgumentException("Invalid type specified. ${type.name()} is not a valid mutation type")
        }
    }

}
