package org.grails.gorm.graphql.entity.dsl

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.gorm.graphql.entity.dsl.helpers.Deprecatable
import org.grails.gorm.graphql.entity.dsl.helpers.Describable
import org.grails.gorm.graphql.entity.dsl.helpers.ExecutesClosures
import org.grails.gorm.graphql.entity.dsl.helpers.Named

/**
 * Builder to provide GraphQL specific data for a GORM entity property
 *
 * Usage:
 * <pre>
 * {@code
 * static graphql = {
 *     someProperty input: false, description: "foo"
 *     otherProperty {
 *         input false
 *         description "otherFoo"
 *     }
 *     //OR: For code completion
 *     otherProperty GraphQLPropertyMapping.build {
 *
 *     }
 *     //If the property name conflicts with a existing method name ex: "description"
 *     property("description") {
 *         ...
 *     }
 *     property "description", [:]
 * }
 * }
 * </pre>
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class GraphQLPropertyMapping implements Describable<GraphQLPropertyMapping>, Deprecatable<GraphQLPropertyMapping>, Named<GraphQLPropertyMapping>, ExecutesClosures {

    /**
     * Whether or not the property should be available to
     * be sent by the client in CREATE or UPDATE operations
     */
    boolean input = true

    /**
     * Whether or not the property should be available to
     * be requested by the client
     */
    boolean output = true

    /**
     * Override whether the property is nullable.
     * Only takes effect for CREATE types
     */
    Boolean nullable

    /**
     * The fetcher to retrieve the property
     */
    Closure dataFetcher

    /**
     * The order the property will be in the schema
     */
    Integer order

    static GraphQLPropertyMapping build(@DelegatesTo(value = GraphQLPropertyMapping, strategy = Closure.DELEGATE_ONLY) Closure closure) {
        GraphQLPropertyMapping mapping = new GraphQLPropertyMapping()
        withDelegate(closure, mapping)
        mapping
    }
}
