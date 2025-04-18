package org.grails.gorm.graphql.entity.dsl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.gorm.graphql.entity.dsl.helpers.Deprecatable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable
import org.grails.gorm.graphql.entity.dsl.helpers.ExecutesClosures
import org.grails.gorm.graphql.entity.operations.ComplexOperation
import org.grails.gorm.graphql.entity.operations.CustomOperation
import org.grails.gorm.graphql.entity.operations.OperationType
import org.grails.gorm.graphql.entity.operations.SimpleOperation
import org.grails.gorm.graphql.entity.property.impl.ComplexGraphQLProperty
import org.grails.gorm.graphql.entity.property.impl.CustomGraphQLProperty
import org.grails.gorm.graphql.entity.property.impl.SimpleGraphQLProperty
import org.grails.gorm.graphql.response.pagination.PaginatedType
import org.springframework.beans.MutablePropertyValues
import org.springframework.validation.DataBinder

/**
 * DSL to provide GraphQL specific data for a GORM entity
 *
 * Usage:
 * <pre>
 * {@code
 * static graphql = {
 *     exclude 'foo'
 *     add('bar', String)
 *     description 'Business users'
 * }
 * //OR: For code completion
 * static graphql = GraphQLMapping.build {
 *     ...
 * }
 * </pre>
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class GraphQLMapping implements Describable<GraphQLMapping>, Deprecatable<GraphQLMapping>, ExecutesClosures {

    private List<CustomGraphQLProperty> additional = []
    private Map<String, GraphQLPropertyMapping> propertyMappings = [:]
    Set<String> excluded = [] as Set
    Operations operations = new Operations()
    private List<CustomOperation> customQueryOperations = []
    private List<CustomOperation> customMutationOperations = []

    List<CustomGraphQLProperty> getAdditional() {
        new ArrayList<CustomGraphQLProperty>(additional)
    }

    Map<String, GraphQLPropertyMapping> getPropertyMappings() {
        new HashMap<String, GraphQLPropertyMapping>(propertyMappings)
    }

    List<CustomOperation> getCustomQueryOperations() {
        new ArrayList<CustomOperation>(customQueryOperations)
    }

    List<CustomOperation> getCustomMutationOperations() {
        new ArrayList<CustomOperation>(customMutationOperations)
    }

    /**
     * Exclude one or more properties from being included in the schema
     *
     * @param properties One or more property names
     */
    void exclude(String... properties) {
        excluded.addAll(properties)
    }

    /**
     * Add a new property to be included in the schema. The property may
     * or may not be backed by an instance method depending on whether or
     * not the property is to be used for response.
     *
     * @param property The property to include
     */
    void add(CustomGraphQLProperty property) {
        property.validate()
        additional.add(property)
    }

    /**
     * Add a new property to be included in the schema. The property may
     * or may not be backed by an instance method depending on whether or
     * not the property is to be used as a part of a response.
     *
     * @param name The name of property to include
     * @param type The returnType of property to include
     * @param closure A closure to further configure the property
     */
    void add(String name, Class type, @DelegatesTo(value = SimpleGraphQLProperty, strategy = Closure.DELEGATE_ONLY) Closure closure = null) {
        CustomGraphQLProperty property = new SimpleGraphQLProperty().name(name).returns(type)
        withDelegate(closure, property)
        add(property)
    }

    /**
     * Add a new property to be included in the schema. The property may
     * or may not be backed by an instance method depending on whether or
     * not the property is to be used as a part of a response. The provided
     * list must contain exactly 1 element that is a class.
     *
     * @param name The name of property to include
     * @param type The returnType of property to include
     * @param closure A closure to further configure the property
     */
    void add(String name, List<Class> type, @DelegatesTo(value = SimpleGraphQLProperty, strategy = Closure.DELEGATE_ONLY) Closure closure = null) {
        CustomGraphQLProperty property = new SimpleGraphQLProperty().name(name).returns(type)
        withDelegate(closure, property)
        add(property)
    }

    /**
     * Add a new property to be included in the schema. The property may
     * or may not be backed by an instance method depending on whether or
     * not the property is to be used for response. Use this method to define
     * a complex type for the property with the returns block.
     *
     * @param name The name of property to include
     * @param typeName The name of the custom type being created
     * @param closure A closure to further configure the property
     */
    void add(String name, String typeName, @DelegatesTo(value = ComplexGraphQLProperty, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        CustomGraphQLProperty property = new ComplexGraphQLProperty().name(name).typeName(typeName)
        withDelegate(closure, property)
        add(property)
    }

    /**
     * Supply metadata about an existing property
     *
     * @param name The property name
     * @param closure The closure to build the metadata
     * @return The property mapping instance
     */
    GraphQLPropertyMapping property(String name, @DelegatesTo(value = GraphQLPropertyMapping, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        GraphQLPropertyMapping mapping = GraphQLPropertyMapping.build(closure)
        property(name, mapping)
    }

    /**
     * Supply metadata about an existing property
     *
     * Example: property('foo', [input: false])
     *
     * @param name The property name
     * @param namedArgs The arguments to build the mapping
     * @return The property mapping instance
     */
    GraphQLPropertyMapping property(String name, Map namedArgs) {
        GraphQLPropertyMapping mapping = new GraphQLPropertyMapping()
        DataBinder dataBinder = new DataBinder(mapping)
        dataBinder.bind(new MutablePropertyValues(namedArgs))
        property(name, mapping)
    }

    /**
     * Supply metadata about an existing property
     *
     * Example: property('foo', input: false)
     *
     * @param name The property name
     * @param namedArgs The arguments to build the mapping
     * @return The property mapping instance
     */
    GraphQLPropertyMapping property(Map namedArgs, String name) {
        property(name, namedArgs)
    }

    /**
     * Supply metadata about an existing property
     *
     * @param name The property name
     * @param mapping The property mapping instance
     * @return The property mapping instance provided
     */
    GraphQLPropertyMapping property(String name, GraphQLPropertyMapping mapping) {
        propertyMappings.put(name, mapping)
        mapping
    }

    /**
     * Supplies configuration for an existing property
     *
     * Usage:
     *
     * foo {
     *     description "Foo"
     * }
     *
     * foo description: "Foo"
     *
     * //Provides code completion
     * foo GraphQLPropertyMapping.build {
     *     description("Foo")
     * }
     *
     * @see GraphQLPropertyMapping
     */
    @CompileDynamic
    Object methodMissing(String name, Object args) {
        if (args && args.getClass().array) {

            if (args[0] instanceof Closure) {
                property(name, (Closure) args[0])
            }
            else if (args[0] instanceof GraphQLPropertyMapping) {
                propertyMappings.put(name, (GraphQLPropertyMapping) args[0])
            }
            else if (args[0] instanceof Map) {
                property(name, (Map) args[0])
            }
            else {
                throw new MissingMethodException(name, getClass(), args)
            }
        }
        else {
            throw new MissingMethodException(name, getClass(), args)
        }
    }

    /**
     * Builder to provide code completion. The mapping instance will not be evaluated
     * until the schema is being generated
     *
     * @param closure The closure to execute in the context of a mapping
     * @return The mapping instance
     */
    static LazyGraphQLMapping lazy(@DelegatesTo(value = GraphQLMapping, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        new LazyGraphQLMapping(closure)
    }

    /**
     * Builder to provide code completion
     *
     * @param closure The closure to execute in the context of a mapping
     * @return The mapping instance
     */
    static GraphQLMapping build(@DelegatesTo(value = GraphQLMapping, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        GraphQLMapping mapping = new GraphQLMapping()
        withDelegate(closure, mapping)
        mapping
    }

    private CustomOperation handleCustomOperation(CustomOperation operation, OperationType type, @DelegatesTo(strategy = Closure.DELEGATE_ONLY)Closure closure) {
        operation.operationType = type
        withDelegate(closure, operation)
        operation.validate()
        operation
    }

    /**
     * Builds a custom query operation with a complex type to be 
     * built in the provided closure.
     *
     * @param name The name used by clients of the GraphQL API to execute the operation
     * @param typeName The name of the custom type returned from the operation
     * @param closure The closure to build the operation
     */
    void query(String name, String typeName, @DelegatesTo(value = ComplexOperation, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        ComplexOperation operation = new ComplexOperation().name(name).typeName(typeName)
        handleCustomOperation(operation, OperationType.QUERY, closure)
        customQueryOperations.add(operation)
    }

    /**
     * Builds a custom query operation. The provided list must ontain exactly 1 
     * element that is a class. This method indicates the return type will be a list.
     *
     * @param name The name used by clients of the GraphQL API to execute the operation
     * @param type The return type. A list with exactly 1 element that is a class. The 
     *             class may be an enum, simple type, or domain class.
     * @param closure The closure to build the operation
     */
    void query(String name, List<Class> type, @DelegatesTo(value = SimpleOperation, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        SimpleOperation operation = new SimpleOperation().name(name).returns(type)
        handleCustomOperation(operation, OperationType.QUERY, closure)
        customQueryOperations.add(operation)
    }

    /**
     * Builds a custom query operation.
     *
     * @param name The name used by clients of the GraphQL API to execute the operation
     * @param type The return type. May be an enum, simple class, or domain class.
     * @param closure The closure to build the operation
     */
    void query(String name, Class type, @DelegatesTo(value = SimpleOperation, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        SimpleOperation operation = new SimpleOperation().name(name).returns(type)
        handleCustomOperation(operation, OperationType.QUERY, closure)
        customQueryOperations.add(operation)
    }

    /**
     * Builds a custom query operation that returns a paginated result.
     *
     * @param name The name used by clients of the GraphQL API to execute the operation
     * @param type The return type. May be an enum, simple class, or domain class.
     * @param closure The closure to build the operation
     */
    void query(String name, PaginatedType type, @DelegatesTo(value = SimpleOperation, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        SimpleOperation operation = new SimpleOperation().name(name).returns(type.type)
        operation.paginated = true
        handleCustomOperation(operation, OperationType.QUERY, closure)
        customQueryOperations.add(operation)
    }

    /**
     * Denotes the return type of an operation should be paginated
     *
     * @param type The domain class being returned
     * @return The type holder
     */
    PaginatedType pagedResult(Class type) {
        new PaginatedType(type: type)
    }

    /**
     * Builds a custom mutation operation with a complex type to be 
     * built in the provided closure.
     *
     * @param name The name used by clients of the GraphQL API to execute the operation
     * @param typeName The name of the custom type returned from the operation
     * @param closure The closure to build the operation
     */
    void mutation(String name, String typeName, @DelegatesTo(value = ComplexOperation, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        ComplexOperation operation = new ComplexOperation().name(name).typeName(typeName)
        handleCustomOperation(operation, OperationType.MUTATION, closure)
        customMutationOperations.add(operation)
    }

    /**
     * Builds a custom mutation operation.
     *
     * @param name The name used by clients of the GraphQL API to execute the operation
     * @param type The return type. May be an enum, simple class, or domain class.
     * @param closure The closure to build the operation
     */
    void mutation(String name, Class type, @DelegatesTo(value = SimpleOperation, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        SimpleOperation operation = new SimpleOperation().name(name).returns(type)
        handleCustomOperation(operation, OperationType.MUTATION, closure)
        customMutationOperations.add(operation)
    }

    /**
     * Builds a custom mutation operation. The provided list must ontain exactly 1 
     * element that is a class. This method indicates the return type will be a list.
     *
     * @param name The name used by clients of the GraphQL API to execute the operation
     * @param type The return type. A list with exactly 1 element that is a class. The 
     *             class may be an enum, simple type, or domain class.
     * @param closure The closure to build the operation
     */
    void mutation(String name, List<Class> type, @DelegatesTo(value = SimpleOperation, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        SimpleOperation operation = new SimpleOperation().name(name).returns(type)
        handleCustomOperation(operation, OperationType.MUTATION, closure)
        customMutationOperations.add(operation)
    }

}
