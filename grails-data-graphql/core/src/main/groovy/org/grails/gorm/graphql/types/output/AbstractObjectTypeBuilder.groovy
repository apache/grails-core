/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.gorm.graphql.types.output

import graphql.TypeResolutionEnvironment
import graphql.schema.GraphQLArgument
import graphql.schema.GraphQLCodeRegistry
import graphql.schema.GraphQLFieldDefinition
import graphql.schema.GraphQLInterfaceType
import graphql.schema.GraphQLObjectType
import graphql.schema.GraphQLOutputType
import graphql.schema.TypeResolver
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.gorm.graphql.GraphQLEntityHelper
import org.grails.gorm.graphql.entity.dsl.helpers.Arguable
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.entity.property.manager.GraphQLDomainPropertyManager
import org.grails.gorm.graphql.response.errors.GraphQLErrorsResponseHandler
import org.grails.gorm.graphql.types.GraphQLPropertyType
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.FieldCoordinates.coordinates
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInterfaceType.newInterface
import static graphql.schema.GraphQLObjectType.newObject

/**
 * A base class used to create object types that represent an entity
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
abstract class AbstractObjectTypeBuilder implements ObjectTypeBuilder {

    protected Map<PersistentEntity, GraphQLOutputType> objectTypeCache = [:]
    protected GraphQLDomainPropertyManager propertyManager
    protected GraphQLTypeManager typeManager
    protected GraphQLErrorsResponseHandler errorsResponseHandler
    protected final GraphQLCodeRegistry.Builder codeRegistry

    AbstractObjectTypeBuilder(GraphQLCodeRegistry.Builder codeRegistry,
                              GraphQLDomainPropertyManager propertyManager,
                              GraphQLTypeManager typeManager,
                              GraphQLErrorsResponseHandler errorsResponseHandler) {
        this.typeManager = typeManager
        this.propertyManager = propertyManager
        this.errorsResponseHandler = errorsResponseHandler
        this.codeRegistry = codeRegistry
    }

    abstract GraphQLDomainPropertyManager.Builder getBuilder()

    abstract GraphQLPropertyType getType()

    protected GraphQLFieldDefinition.Builder buildField(GraphQLDomainProperty prop, String parentType) {
        GraphQLFieldDefinition.Builder field = newFieldDefinition()
                .name(prop.name)
                .deprecate(prop.deprecationReason)
                .description(prop.description)

        GraphQLOutputType type = (GraphQLOutputType) prop.getGraphQLType(typeManager, type)
        if (prop.dataFetcher != null) {
            codeRegistry.dataFetcher(
                    coordinates(parentType, prop.name),
                    prop.dataFetcher
            )
        }
        field.type(type)

        field
    }

    protected GraphQLFieldDefinition.Builder addFieldArgs(GraphQLFieldDefinition.Builder field, GraphQLDomainProperty prop, MappingContext mapping) {
        if (prop instanceof Arguable) {
            List<GraphQLArgument> arguments = prop.getArguments(typeManager, mapping)
            if (!arguments.empty) {
                field.arguments(arguments)
            }
        }
        field
    }

    @Override
    GraphQLOutputType build(PersistentEntity entity) {

        GraphQLOutputType objectType

        if (objectTypeCache.containsKey(entity)) {
            objectTypeCache.get(entity)
        }
        else {
            final String description = GraphQLEntityHelper.getDescription(entity)
            final String name = typeManager.namingConvention.getType(entity, type)

            List<GraphQLFieldDefinition> fields = new ArrayList<>(properties.size() + 1)

            List<GraphQLDomainProperty> properties = builder.getProperties(entity)
            for (GraphQLDomainProperty prop: properties) {
                if (prop.output) {
                    GraphQLFieldDefinition.Builder field = buildField(prop, name)
                    addFieldArgs(field, prop, entity.mappingContext)
                    fields.add(field.build())
                }
            }

            if (errorsResponseHandler != null) {
                GraphQLFieldDefinition fieldDefinition = errorsResponseHandler.getFieldDefinition(typeManager, name)
                fields.add(fieldDefinition)
            }

            boolean hasChildEntities = entity.root && !entity.mappingContext.getDirectChildEntities(entity).empty

            if (hasChildEntities && !type.embedded) {
                objectType = buildInterfaceType(entity, name, description, fields)
            }
            else {
                objectType = buildObjectType(entity, name, description, fields)
            }

            objectTypeCache.put(entity, objectType)
            objectType
        }
    }

    GraphQLObjectType buildObjectType(final PersistentEntity entity, final String name, final String description, final List<GraphQLFieldDefinition> fields) {

        GraphQLObjectType.Builder obj = newObject()
                .name(name)
                .description(description)
                .fields(fields)

        if (!entity.root) {
            obj.withInterface(typeManager.createReference(entity.rootEntity, GraphQLPropertyType.OUTPUT))
        }

        obj.build()
    }

    GraphQLInterfaceType buildInterfaceType(final PersistentEntity entity, final String name, final String description, final List<GraphQLFieldDefinition> fields) {

        GraphQLInterfaceType.Builder obj = newInterface()
                .name(name)
                .description(description)
                .fields(fields)
                .typeResolver(buildEntityTypeResolver())

        obj.build()
    }

    protected TypeResolver buildEntityTypeResolver() {
        { TypeResolutionEnvironment env ->
            final String typeName = typeManager.namingConvention.getType(env.object.class.simpleName, GraphQLPropertyType.OUTPUT)
            (GraphQLObjectType) env.schema.getType(typeName)
        } as TypeResolver
    }

}
