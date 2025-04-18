package org.grails.gorm.graphql.entity.dsl.helpers

import graphql.schema.*
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.fields.ComplexField
import org.grails.gorm.graphql.entity.fields.Field
import org.grails.gorm.graphql.entity.fields.SimpleField
import org.grails.gorm.graphql.types.GraphQLTypeManager

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition
import static graphql.schema.GraphQLInputObjectField.newInputObjectField

/**
 * Decorates a class with the ability to build a custom type
 *
 * @param <T> The implementing class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait ComplexTyped<T> extends ExecutesClosures {

    boolean collection = false

    T collection(boolean collection) {
        this.collection = collection
        (T)this
    }

    List<Field> fields = []

    boolean defaultNull = true

    T defaultNull(boolean defaultNull) {
        this.defaultNull = defaultNull
        (T)this
    }

    /**
     * Builds a custom object returnType if the supplied return returnType is a Map
     *
     * @param typeManager The returnType manager
     * @param mappingContext The mapping context
     * @return The custom returnType
     */
    GraphQLOutputType buildCustomType(String name, GraphQLTypeManager typeManager, MappingContext mappingContext) {
        GraphQLObjectType.Builder builder = GraphQLObjectType.newObject()
                .name(name)

        for (Field field: fields) {
            if (field.output) {
                builder.field(newFieldDefinition()
                        .name(field.name)
                        .description(field.description)
                        .deprecate(field.deprecationReason)
                        .type(field.getType(typeManager, mappingContext)))
            }
        }
        GraphQLObjectType type = builder.build()

        if (collection) {
            GraphQLList.list(type)
        }
        else {
            type
        }
    }

    private GraphQLInputType customInputType

    /**
     * Builds a custom object returnType if the supplied return returnType is a Map
     *
     * @param typeManager The returnType manager
     * @param mappingContext The mapping context
     * @return The custom returnType
     */
    GraphQLInputType buildCustomInputType(String name, GraphQLTypeManager typeManager, MappingContext mappingContext, boolean nullable) {
        if (customInputType == null) {
            GraphQLInputObjectType.Builder builder = GraphQLInputObjectType.newInputObject()
                    .name(name)

            for (Field field: fields) {
                if (field.input) {
                    builder.field(newInputObjectField()
                            .name(field.name)
                            .description(field.description)
                            .defaultValue(field.defaultValue)
                            .type(field.getInputType(typeManager, mappingContext)))
                }
            }
            GraphQLInputType type = builder.build()

            if (!nullable) {
                type = GraphQLNonNull.nonNull(type)
            }

            if (collection) {
                type = GraphQLList.list((GraphQLInputType) type)
            }
            customInputType = (GraphQLInputType) type
        }

        customInputType
    }

    private void handleField(@DelegatesTo(strategy = Closure.DELEGATE_ONLY)Closure closure, Field field) {
        field.nullable(defaultNull)
        withDelegate(closure, (Object)field)
        handleField(field)
    }

    private void handleField(Field field) {
        field.validate()
        fields.add(field)
    }

    void field(String name, List<Class> type, @DelegatesTo(value = SimpleField, strategy = Closure.DELEGATE_ONLY) Closure closure = null) {
        Field field = new SimpleField().name(name).returns(type)
        handleField(closure, field)
    }

    void field(String name, Class type, @DelegatesTo(value = SimpleField, strategy = Closure.DELEGATE_ONLY) Closure closure = null) {
        Field field = new SimpleField().name(name).returns(type)
        handleField(closure, field)
    }

    void field(String name, String typeName, @DelegatesTo(value = ComplexField, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        Field field = new ComplexField().name(name).typeName(typeName)
        handleField(closure, field)
    }

    void field(ComplexField field) {
        handleField(field)
    }

}
