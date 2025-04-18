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
package org.grails.datastore.gorm.neo4j.collection

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.neo4j.CypherBuilder
import org.grails.datastore.gorm.neo4j.RelationshipPersistentEntity
import org.grails.datastore.gorm.neo4j.engine.Neo4jEntityPersister
import org.grails.datastore.gorm.query.AbstractResultList
import org.grails.datastore.mapping.engine.EntityPersister
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.query.QueryException
import org.neo4j.driver.Record
import org.neo4j.driver.Result
import org.neo4j.driver.Value
import org.neo4j.driver.types.Entity
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Relationship

import javax.persistence.LockModeType


/**
 * A Neo4j result list for decoding objects from the {@link Result} interface
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class Neo4jResultList extends AbstractResultList {

    private static final Map<Association, Object> EMPTY_ASSOCIATIONS = Collections.<Association, Object> emptyMap()
    private static final Map<String, Object> EMPTY_RESULT_DATA = Collections.<String, Object> emptyMap()

    final protected transient  Neo4jEntityPersister entityPersister

    protected transient Map<Association, Object> initializedAssociations = EMPTY_ASSOCIATIONS
    protected transient Map<Serializable, Node> initializedNodes = [:]

    protected final LockModeType lockMode

    Neo4jResultList(int offset, Result cursor, Neo4jEntityPersister entityPersister, LockModeType lockMode = LockModeType.NONE) {
        super(offset, (Iterator<Object>)cursor)
        this.entityPersister = entityPersister
        this.lockMode = lockMode
    }

    Neo4jResultList(int offset, Iterator<Object> cursor, Neo4jEntityPersister entityPersister) {
        super(offset, cursor)
        this.entityPersister = entityPersister
        this.lockMode = LockModeType.NONE
    }

    Neo4jResultList(int offset, Integer size, Iterator<Object> cursor, Neo4jEntityPersister entityPersister) {
        super(offset, size, cursor)
        this.entityPersister = entityPersister
        this.lockMode = LockModeType.NONE
    }

    /**
     * Set any already initialized associations to avoid extra proxy queries
     *
     * @param initializedAssociations
     */
    void setInitializedAssociations(Map<Association, Object> initializedAssociations) {
        this.initializedAssociations = initializedAssociations
    }

    /**
     * Adds an initialized node
     *
     * @param node The node
     */
    void addInitializedNode(Node node) {
        this.initializedNodes.put(node.id(), node)
    }

    @Override
    protected Object nextDecoded() {
        return nextDecodedInternal()
    }

    private Object nextDecodedInternal() {
        def next = cursor.next()
        if (next instanceof Node) {
            Node node = (Node) next
            return entityPersister.unmarshallOrFromCache(entityPersister.getPersistentEntity(), node, EMPTY_RESULT_DATA, initializedAssociations, lockMode)
        }
        else if(next instanceof Relationship) {
            PersistentEntity persistentEntity = entityPersister.getPersistentEntity()
            if(persistentEntity instanceof RelationshipPersistentEntity) {
                Relationship data = (Relationship) next
                return entityPersister.unmarshallOrFromCache(entityPersister.getPersistentEntity(), data, initializedAssociations, initializedNodes)
            }
            else {
                throw new QueryException("Query must return a node as the first column of the RETURN statement")
            }
        }
        else {
            Record record = (Record) next
            if (record.containsKey(CypherBuilder.NODE_DATA)) {
                Node data = (Node) record.get(CypherBuilder.NODE_DATA).asNode()
                return entityPersister.unmarshallOrFromCache(entityPersister.getPersistentEntity(), data, record.asMap(), initializedAssociations, lockMode)
            }
            else if(record.containsKey(CypherBuilder.REL_DATA)) {
                PersistentEntity persistentEntity = entityPersister.getPersistentEntity()
                if(persistentEntity instanceof RelationshipPersistentEntity) {
                    def recordMap = record.asMap()
                    RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity)persistentEntity
                    Relationship data = (Relationship) record.get(CypherBuilder.REL_DATA).asRelationship()
                    this.initializedAssociations = new LinkedHashMap<>(initializedAssociations)
                    if(record.containsKey(RelationshipPersistentEntity.FROM)) {
                        Association fromAssociation = relEntity.getFrom()
                        PersistentEntity fromEntity = fromAssociation.getAssociatedEntity()
                        Neo4jEntityPersister fromPersister = entityPersister.getSession().getEntityPersister(fromEntity)
                        this.initializedAssociations.put(fromAssociation,
                                fromPersister.unmarshallOrFromCache( fromEntity, record.get(RelationshipPersistentEntity.FROM).asNode(),recordMap,  initializedAssociations)
                        )
                    }
                    if(record.containsKey(RelationshipPersistentEntity.TO)) {
                        Association toAssociation = relEntity.getTo()
                        PersistentEntity toEntity = toAssociation.getAssociatedEntity()
                        Neo4jEntityPersister fromPersister = entityPersister.getSession().getEntityPersister(toEntity)
                        this.initializedAssociations.put(toAssociation,
                                fromPersister.unmarshallOrFromCache( toEntity, record.get(RelationshipPersistentEntity.TO).asNode() ,recordMap,  initializedAssociations)
                        )
                    }
                    return entityPersister.unmarshallOrFromCache(entityPersister.getPersistentEntity(), data, initializedAssociations, initializedNodes)
                }
                else {
                    throw new QueryException("Query must return a node as the first column of the RETURN statement")
                }
            }
            else {

                Node node = record.values().find() {  Value v ->
                    v.type() == entityPersister.getSession().boltDriver.defaultTypeSystem().NODE()
                }?.asNode()

                if (node != null) {
                    return entityPersister.unmarshallOrFromCache(entityPersister.getPersistentEntity(), node, record.asMap(), initializedAssociations, lockMode)
                } else {
                    throw new QueryException("Query must return a node as the first column of the RETURN statement")
                }
            }
        }
    }

    @Override
    void close() throws IOException {
        // no-op
    }
}
