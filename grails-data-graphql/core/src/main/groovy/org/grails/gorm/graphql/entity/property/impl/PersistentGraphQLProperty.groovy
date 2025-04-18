package org.grails.gorm.graphql.entity.property.impl

import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.PersistentEntityValidator
import graphql.schema.DataFetcher
import graphql.schema.GraphQLType
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.gorm.graphql.GraphQL
import org.grails.gorm.graphql.Schema
import org.grails.gorm.graphql.entity.dsl.GraphQLPropertyMapping
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.fetcher.impl.ClosureDataFetcher
import org.grails.gorm.graphql.fetcher.impl.PersistentPropertyDataFetcher
import org.grails.gorm.graphql.types.GraphQLOperationType
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager
import org.springframework.validation.Validator

import java.lang.reflect.Field

import static graphql.schema.GraphQLList.list

/**
 * Implementation of {@link GraphQLDomainProperty} to represent a property
 * on a GORM entity
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class PersistentGraphQLProperty extends OrderedGraphQLProperty {

    final Integer order
    final String name
    final Class type
    final boolean collection
    final boolean nullable
    String description
    String deprecationReason
    final boolean input
    final boolean output
    final DataFetcher dataFetcher

    PersistentProperty property
    private MappingContext mappingContext

    private static final int DEFAULT_ID_ORDER = -20
    private static final int DEFAULT_VERSION_ORDER = -10

    PersistentGraphQLProperty(MappingContext mappingContext, PersistentProperty property, GraphQLPropertyMapping mapping) {
        this.property = property
        this.mappingContext = mappingContext
        this.name = mapping.name ?: property.name
        this.type = getBaseType(property)
        this.collection = (property instanceof ToMany)
        if (mapping.nullable != null) {
            this.nullable = mapping.nullable
        }
        else {
            this.nullable = ((Property) property.mapping.mappedForm).nullable
        }
        this.output = mapping.output
        this.input = mapping.input
        this.dataFetcher = mapping.dataFetcher ? new ClosureDataFetcher(mapping.dataFetcher) : new PersistentPropertyDataFetcher((PersistentProperty) property)
        if (mapping.order != null) {
            this.order = mapping.order
        }
        else {
            Validator validator = mappingContext.getEntityValidator(property.owner)
            if (validator instanceof PersistentEntityValidator) {
                ConstrainedProperty constrainedProperty = ((PersistentEntityValidator) validator).constrainedProperties.get(name)
                if (constrainedProperty != null) {
                    this.order = constrainedProperty.order
                }
            }
        }
        if (this.order == null) {
            if (isIdentifier(property.owner, name)) {
                this.order = DEFAULT_ID_ORDER
            }
            else if (name == 'version') {
                this.order = DEFAULT_VERSION_ORDER
            }
        }

        initializeMetadata(mapping)
    }

    private void initializeMetadata(GraphQLPropertyMapping mapping) {
        this.description = mapping.description
        this.deprecationReason = mapping.deprecationReason
        try {
            Field field = property.owner.javaClass.getDeclaredField(property.name)
            if (field != null) {
                GraphQL graphQL = field.getAnnotation(GraphQL)
                if (graphQL != null) {
                    if (description == null && !graphQL.value().empty) {
                        description = graphQL.value()
                    }
                    if (deprecationReason == null && !graphQL.deprecationReason().empty) {
                        deprecationReason = graphQL.deprecationReason()
                    }
                    if (graphQL.deprecated() && deprecationReason == null) {
                        deprecationReason = Schema.DEFAULT_DEPRECATION_REASON
                    }
                }
                if (field.getAnnotation(Deprecated) != null && deprecationReason == null) {
                    deprecationReason = Schema.DEFAULT_DEPRECATION_REASON
                }
            }
        } catch (NoSuchFieldException e) { }

        if (mapping.deprecated && deprecationReason == null) {
            deprecationReason = Schema.DEFAULT_DEPRECATION_REASON
        }
    }

    protected Class getBaseType(PersistentProperty property) {
        if (property instanceof Association) {
            Association association = (Association)property
            if (association.basic) {
                ((Basic) property).componentType
            }
            else {
                association.associatedEntity.javaClass
            }
        }
        else {
            property.type
        }
    }

    @Override
    boolean isDeprecated() {
        deprecationReason != null
    }

    @Override
    GraphQLType getGraphQLType(GraphQLTypeManager typeManager, GraphQLPropertyType propertyType) {
        GraphQLType graphQLType

        if (type.enum) {
            graphQLType = typeManager.getEnumType(type, nullable)
        }
        else {
            boolean embedded = false
            PersistentEntity entity
            if (property instanceof Association) {
                Association association = ((Association)property)
                entity = association.associatedEntity
                embedded = association.embedded
            }
            if (entity == null) {
                entity = mappingContext.getPersistentEntity(type.name)
            }
            if (entity != null) {
                if (propertyType.operationType == GraphQLOperationType.OUTPUT) {
                    if (embedded) {
                        graphQLType = typeManager.getQueryType(entity, propertyType.embeddedType)
                    }
                    else {
                        graphQLType = typeManager.getQueryType(entity,  GraphQLPropertyType.OUTPUT)
                    }
                }
                else {
                    GraphQLPropertyType mutationType
                    if (embedded) {
                        mutationType = propertyType.embeddedType
                    }
                    else {
                        mutationType = propertyType.nestedType
                    }
                    graphQLType = typeManager.getMutationType(entity, mutationType, nullable)
                }
            }
            else {
                graphQLType = typeManager.getType(type, nullable)
            }
        }

        if (collection) {
            graphQLType = list(graphQLType)
        }

        graphQLType
    }

    private boolean isIdentifier(PersistentEntity entity, String name) {
        if (entity.identity != null) {
            return entity.identity.name == name
        }
        else if (entity.compositeIdentity != null) {
            for (PersistentProperty property: entity.compositeIdentity) {
                if (property.name == name) {
                    return true
                }
            }
        }
        false
    }
}
