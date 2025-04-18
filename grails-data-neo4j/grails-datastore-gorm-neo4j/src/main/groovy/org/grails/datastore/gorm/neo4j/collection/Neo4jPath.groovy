package org.grails.datastore.gorm.neo4j.collection

import grails.neo4j.Neo4jEntity
import grails.neo4j.Path
import grails.neo4j.Relationship
import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.neo4j.GraphPersistentEntity
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jMappingContext
import org.grails.datastore.gorm.neo4j.engine.Neo4jEntityPersister
import org.grails.datastore.mapping.query.QueryException
import org.neo4j.driver.types.Node

/**
 * A neo4j {@link org.neo4j.driver.types.Path} adapter
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
class Neo4jPath<S extends Neo4jEntity<S>, E extends Neo4jEntity<E>> implements Path<S, E> {
    final Neo4jDatastore datastore
    final org.neo4j.driver.types.Path neo4jPath
    final GraphPersistentEntity from
    final GraphPersistentEntity to

    private S start
    private E end
    private Iterable nodes

    Neo4jPath(Neo4jDatastore datastore, org.neo4j.driver.types.Path neo4jPath, GraphPersistentEntity from, GraphPersistentEntity to) {
        this.datastore = datastore
        this.neo4jPath = neo4jPath
        if(from == null) {
            from = datastore.mappingContext.findPersistentEntityForLabels(neo4jPath.start().labels())
        }
        if(to == null) {
            to = datastore.mappingContext.findPersistentEntityForLabels(neo4jPath.end().labels())
        }
        if(from == null) {
            throw new IllegalArgumentException("From domain class type cannot be established for path [$neo4jPath]")
        }
        if(to == null) {
            throw new IllegalArgumentException("From domain class type cannot be established for path [$neo4jPath]")
        }
        this.from = from
        this.to = to
    }

    Neo4jPath(Neo4jDatastore datastore, org.neo4j.driver.types.Path neo4jPath, Class from, Class to) {
        this(datastore, neo4jPath,
                (GraphPersistentEntity)datastore.mappingContext.getPersistentEntity(from.name),
                (GraphPersistentEntity)datastore.mappingContext.getPersistentEntity(to.name))
    }

    Neo4jPath(Neo4jDatastore datastore, org.neo4j.driver.types.Path neo4jPath) {
        this(datastore, neo4jPath,(GraphPersistentEntity)null,null)
    }



    @Override
    S start() {
        if(start == null) {
            Class clazz = from.javaClass
            Neo4jEntityPersister persister = (Neo4jEntityPersister )GormEnhancer.findDatastore(clazz).currentSession.getPersister(clazz)
            start = (S)persister.unmarshallOrFromCache(from, neo4jPath.start())
        }
        return start
    }

    @Override
    E end() {
        if(end == null) {
            Class clazz = to.javaClass
            Neo4jEntityPersister persister = (Neo4jEntityPersister )GormEnhancer.findDatastore(clazz).currentSession.getPersister(clazz)

            end = (E)persister.unmarshallOrFromCache(to, neo4jPath.end())
        }
        return end
    }

    @Override
    int length() {
        return neo4jPath.length()
    }

    @Override
    Iterable nodes() {
        if(nodes == null) {
            List nodeList = []
            for(Node n in neo4jPath.nodes()) {
                nodeList.add(unmarshallNode(datastore, n))
            }
            nodes = nodeList
        }
        return nodes
    }

    @Override
    boolean contains(Object o) {
        nodes().contains(o)
    }

    @Override
    Iterator<Path.Segment> iterator() {
        return new Neo4jPathIterator(datastore, neo4jPath.iterator())
    }

    static class Neo4jPathIterator implements Iterator<Path.Segment> {
        final Neo4jDatastore datastore
        final Iterator<org.neo4j.driver.types.Path.Segment> iterator

        Neo4jPathIterator(Neo4jDatastore datastore, Iterator<org.neo4j.driver.types.Path.Segment> iterator) {
            this.datastore = datastore
            this.iterator = iterator
        }

        @Override
        boolean hasNext() {
            iterator.hasNext()
        }

        @Override
        Path.Segment next() {
            org.neo4j.driver.types.Path.Segment neoSegment = iterator.next()
            return new Neo4jPathSegment(datastore,neoSegment)
        }
    }

    static class Neo4jPathSegment implements  Path.Segment {
        final Neo4jDatastore datastore
        final org.neo4j.driver.types.Path.Segment neoSegment

        private Neo4jEntity start
        private Neo4jEntity end

        Neo4jPathSegment(Neo4jDatastore datastore, org.neo4j.driver.types.Path.Segment neoSegment) {
            this.datastore = datastore
            this.neoSegment = neoSegment
        }

        @Override
        Relationship relationship() {
            return new Neo4jRelationship(start(), end(), neoSegment.relationship().type())
        }

        @Override
        Neo4jEntity start() {
            if(start == null)
                start = unmarshallNode(datastore, neoSegment.start())
            return start
        }

        @Override
        Neo4jEntity end() {
            if(end == null) {
                end = unmarshallNode(datastore, neoSegment.end())
            }
            return end
        }


    }

    protected static Neo4jEntity unmarshallNode(Neo4jDatastore datastore, Node node) {
        Iterable<String> labels = node.labels()
        GraphPersistentEntity entity = datastore.getMappingContext().findPersistentEntityForLabels(labels)
        if (entity == null) {
            throw new QueryException("Cannot establish entity for labels [$labels] from node [$node]")
        }
        Neo4jEntityPersister persister = (Neo4jEntityPersister) datastore.currentSession.getPersister(entity)
        return (Neo4jEntity) persister.unmarshallOrFromCache(entity, node)
    }

}
