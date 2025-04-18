package org.grails.gorm.graphql.entity.dsl.helpers

import graphql.schema.GraphQLArgument
import groovy.transform.CompileStatic
import org.grails.datastore.mapping.model.MappingContext
import org.grails.gorm.graphql.entity.arguments.ComplexArgument
import org.grails.gorm.graphql.entity.arguments.CustomArgument
import org.grails.gorm.graphql.entity.arguments.SimpleArgument
import org.grails.gorm.graphql.types.GraphQLTypeManager

/**
 * Decorates a class with a description property and builder method.
 *
 * @param <T> The implementing class
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
trait Arguable<T> extends ExecutesClosures {

    List<CustomArgument> arguments = []

    private void handleArgumentClosure(CustomArgument argument, @DelegatesTo(strategy = Closure.DELEGATE_ONLY)Closure closure) {
        withDelegate(closure, (Object)argument)
        argument.validate()
        arguments.add(argument)
    }

    List<GraphQLArgument> getArguments(GraphQLTypeManager typeManager, MappingContext mappingContext) {
        arguments.collect {
            it.getArgument(typeManager, mappingContext).build()
        }
    }

    /**
     * Creates an argument to the operation that is a list of a simple type.
     * The list can not have more than 1 element and that element must be a class.
     *
     * @param name The name of the argument
     * @param type The returnType of the argument
     * @param closure To provide additional data about the argument
     * @return The operation in order to chain method calls
     */
    T argument(String name, List<Class<?>> type, @DelegatesTo(value = SimpleArgument, strategy = Closure.DELEGATE_ONLY) Closure closure = null) {
        CustomArgument argument = new SimpleArgument().name(name).returns(type)
        handleArgumentClosure(argument, closure)
        (T)this
    }

    /**
     * Creates an argument to the operation that is of the returnType provided.
     *
     * @param name The name of the argument
     * @param type The returnType of the argument
     * @param closure To provide additional data about the argument
     * @return The operation in order to chain method calls
     */
    T argument(String name, Class<?> type, @DelegatesTo(value = SimpleArgument, strategy = Closure.DELEGATE_ONLY) Closure closure = null) {
        CustomArgument argument = new SimpleArgument().name(name).returns(type)
        handleArgumentClosure(argument, closure)
        (T)this
    }

    /**
     * Creates an argument to the operation that is a custom type.
     *
     * @param name The name of the argument
     * @param closure To provide additional data about the argument
     * @return The operation in order to chain method calls
     */
    T argument(String name, String typeName, @DelegatesTo(value = ComplexArgument, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        CustomArgument argument = new ComplexArgument().name(name).typeName(typeName)
        handleArgumentClosure(argument, closure)
        (T)this
    }

}
