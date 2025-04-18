/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.neo4j.extensions

import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.gorm.neo4j.collection.Neo4jPath
import org.grails.datastore.gorm.neo4j.collection.Neo4jResultList
import org.grails.datastore.mapping.core.AbstractDatastore
import org.neo4j.driver.Result
import org.neo4j.driver.QueryRunner
import org.neo4j.driver.types.MapAccessor
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Path
import org.neo4j.driver.types.Relationship

/**
 * Extension methods to improve the Neo4j experience in Groovy.
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class Neo4jExtensions {

    /**
     * Allows the subscript operator on nodes
     */
    static Object getAt(MapAccessor node, String name) {
        node.get(name)
    }

    /**
     * Allows the dot operator on nodes
     */
    static Object getProperty(MapAccessor node, String name) {
        node.get(name)
    }

    /**
     * Allows the subscript operator on nodes
     */
    static Boolean asBoolean(MapAccessor node) {
        return node.size() > 0
    }

    /**
     * Allow casting from Node to domain class
     *
     * @param node The node
     *
     * @param c The domain class type
     * @return The domain instance
     */
    static <N> N asType(Node node, Class<N> c) {
        //TODO: Remove explicit typecast to Class<?> once GROOVY-9460 is resolved
        if(Map.isAssignableFrom((Class<?>) c)) {
            return (N)node.asMap()
        }
        else {
            Neo4jSession session = (Neo4jSession)AbstractDatastore.retrieveSession(Neo4jDatastore)
            def entityPersister = session.getEntityPersister(c)
            if(entityPersister != null) {
                return (N)entityPersister.unmarshallOrFromCache(entityPersister.getPersistentEntity(), node)
            }
            else {
                throw new ClassCastException("Class [$c.name] is not a GORM entity")
            }
        }
    }

    /**
     * Allow casting from Relationship to a relationship domain class
     *
     * @param rel The rel
     *
     * @param c The domain class type
     * @return The domain instance
     */
    static <N> N asType(Relationship rel, Class<N> c) {
        Neo4jSession session = (Neo4jSession)AbstractDatastore.retrieveSession(Neo4jDatastore)
        def entityPersister = session.getEntityPersister(c)
        if(entityPersister != null) {
            return (N)entityPersister.unmarshallOrFromCache(entityPersister.getPersistentEntity(), rel, Collections.emptyMap(), Collections.emptyMap())
        }
        else {
            throw new ClassCastException("Class [$c.name] is not a GORM relationship entity")
        }
    }

    /**
     * Allow casting from a Neo4j path to a GORM Path
     *
     * @param path The path
     *
     * @param c The domain class type
     * @return The domain instance
     */
    static grails.neo4j.Path asType(Path path, Class<grails.neo4j.Path> c) {
        if(grails.neo4j.Path.isAssignableFrom(c)) {
            Neo4jSession session = (Neo4jSession)AbstractDatastore.retrieveSession(Neo4jDatastore)
            return new Neo4jPath<>(session.datastore, path)
        }
        else {
            throw new GroovyCastException(path, c)
        }
    }

    /**
     * Allow casting from Result to domain class
     *
     * @param node The node
     *
     * @param c The domain class type
     * @return The domain instance
     */
    static <N> N asType(Result result, Class<N> c) {
        Neo4jSession session = (Neo4jSession)AbstractDatastore.retrieveSession(Neo4jDatastore)
        def entityPersister = session.getEntityPersister(c)
        if(entityPersister != null) {
            def resultList = new Neo4jResultList(0, result, entityPersister)
            if(!resultList.isEmpty()) {
                return (N)resultList.get(0)
            }
            else {
                return null
            }
        }
        else {
            throw new ClassCastException("Class [$c.name] is not a GORM entity")
        }
    }

    /**
     * Allow casting from Result to a list of domain class
     *
     * @param node The node
     *
     * @param c The domain class type
     * @return The domain instance
     */
    static <N> List<N> toList(Result result, Class<N> c) {
        Neo4jSession session = (Neo4jSession)AbstractDatastore.retrieveSession(Neo4jDatastore)
        def entityPersister = session.getEntityPersister(c)
        if(entityPersister != null) {
            return new Neo4jResultList(0, result, entityPersister)
        }
        else {
            throw new ClassCastException("Class [$c.name] is not a GORM entity")
        }
    }

    /**
     * Executes a cypher query with positional parameters
     *
     * @param databaseService The GraphDatabaseService
     * @param cypher The cypher query
     * @param positionalParameters The position parameters
     * @return The query result
     */
    static Result execute(QueryRunner session, String cypher, List<Object> positionalParameters) {
        Map<String,Object> params = new LinkedHashMap<>()
        int i = 0;
        for(p in positionalParameters) {
            params.put(String.valueOf(++i), p)
        }
        session.run(cypher, params)
    }

}
