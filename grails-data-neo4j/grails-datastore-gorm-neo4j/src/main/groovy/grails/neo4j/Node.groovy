package grails.neo4j

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.neo4j.api.Neo4jGormStaticApi
import org.grails.datastore.gorm.schemaless.DynamicAttributes

/**
 * A domain class that represents a Neo4j Node
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait Node<D> implements Neo4jEntity<D>, GormEntity<D>, DynamicAttributes {
    /**
     * Allows accessing to dynamic properties with the dot operator
     *
     * @param instance The instance
     * @param name The property name
     * @return The property value
     */
    def propertyMissing(String name) {
        return getAt(name)
    }

    /**
     * Allows setting a dynamic property via the dot operator
     * @param instance The instance
     * @param name The property name
     * @param val The value
     */
    def propertyMissing(String name, val) {
        DynamicAttributes.super.putAt(name, val)
    }

    /**
     * Execute cypher that finds a path to the given entity
     *
     * @param cypher The cypher
     * @return The path
     */
    static Path<D, D> findPath(CharSequence cypher) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findPath(cypher, Collections.emptyMap())
    }

    /**
     * Finds a path between two entities
     *
     * @param from The from entity (can be a proxy)
     * @param to The to entity (can be a proxy)
     * @param maxDistance The maximum distance to traverse (defaults to 10)
     * @return The path or null if non exists
     */
    static <F, T> Path<F, T> findShortestPath(F from, T to, int maxDistance = 10) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findShortestPath(from, to, maxDistance)
    }

    /**
     * Finds a relationship between two entities
     *
     * @param from The from entity (can be a proxy)
     * @param to The to entity (can be a proxy)
     * @return The relationship or null if it doesn't exist
     */
    static <F extends GormEntity, T extends GormEntity> Relationship<F, T> findRelationship(F from, T to) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findRelationship(from, to)
    }

    /**
     * Finds a relationship between two entities
     *
     * @param from The from entity (can be a proxy)
     * @param to The to entity (can be a proxy)
     * @return The relationship or null if it doesn't exist
     */
    static <F extends GormEntity, T extends GormEntity> List<Relationship<F, T>> findRelationships(F from, T to, Map params = Collections.emptyMap()) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findRelationships(from, to, params)
    }

    /**
     * Finds a relationship between two entity types
     *
     * @param from The from entity (can be a proxy)
     * @param to The to entity (can be a proxy)
     * @return The relationship or null if it doesn't exist
     */
    static <F extends GormEntity, T extends GormEntity> List<Relationship<F, T>> findRelationships(Class<F> from, Class<T> to, Map params = Collections.emptyMap()) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findRelationships(from, to, params)
    }
    /**
     * Execute cypher that finds a path to the given entity
     *
     * @param cypher The cypher
     * @param params The parameters
     * @return The path or null if non exists
     */
    static Path<D, D> findPath(CharSequence cypher, Map params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findPath(cypher, params)
    }

    /**
     * Execute cypher that finds a path to the given entity
     *
     * @param type The type to find a path to
     * @param cypher The cypher
     * @return The path
     */
    static <T> Path<D, T> findPathTo(Class<T> type, CharSequence cypher) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findPathTo(type, cypher, Collections.emptyMap())
    }

    /**
     * Execute cypher that finds a path to the given entity
     *
     * @param cypher The cypher
     * @return The path
     */
    static <T> Path<D, T> findPathTo(Class<T> type, CharSequence cypher, Map params) {
        ((Neo4jGormStaticApi) GormEnhancer.findStaticApi(this)).findPathTo(type, cypher, params)
    }
}