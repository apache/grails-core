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
package org.grails.datastore.gorm.neo4j.engine;

import grails.neo4j.Relationship;
import org.grails.datastore.gorm.neo4j.*;
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicAssociation;
import org.grails.datastore.gorm.neo4j.mapping.config.DynamicToOneAssociation;
import org.grails.datastore.mapping.core.impl.PendingInsertAdapter;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.types.*;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Represents a pending relationship insert
 *
 * @author Stefan
 * @author Graeme Rocher
 *
 */
public class RelationshipPendingInsert extends PendingInsertAdapter<Object, Serializable> {
    /**
     * The name of the from property
     */
    public static final String FROM = RelationshipPersistentEntity.FROM;
    /**
     * The name of the to property
     */
    public static final String TO = RelationshipPersistentEntity.TO;
    public static final String SOURCE_TYPE = "sourceType";
    public static final String TARGET_TYPE = "targetType";

    private static Logger log = LoggerFactory.getLogger(RelationshipPendingInsert.class);
    private final Transaction boltTransaction;
    private final Association association;
    private final Collection<Serializable> targetIdentifiers;
    private final boolean isUpdate;
    private final Neo4jSession session;

    public RelationshipPendingInsert(EntityAccess parent, Association association, Collection<Serializable> pendingInserts, Neo4jSession session, boolean isUpdate) {
        super(parent.getPersistentEntity(), -1L, parent.getEntity(), parent);

        this.boltTransaction = session.getTransaction().getTransaction();
        this.session = session;
        this.targetIdentifiers = pendingInserts;
        this.association = association;
        this.isUpdate = isUpdate;
    }

    @Override
    public void run() {


        GraphPersistentEntity graphParent = (GraphPersistentEntity) getEntity();
        GraphPersistentEntity graphChild = (GraphPersistentEntity) association.getAssociatedEntity();
        Map<String,Object> params = new LinkedHashMap<>(2);
        EntityAccess entityAccess = getEntityAccess();
        Object parentId = entityAccess.getIdentifier();

        final boolean isRelationshipAssociation = Relationship.class.isAssignableFrom(graphParent.getJavaClass());
        if(isRelationshipAssociation) {
            if(association.getName().equals(FROM)) {
                Association endProperty = (Association) graphParent.getPropertyByName(TO);
                Association startProperty = (Association) graphParent.getPropertyByName(FROM);

                graphChild = (GraphPersistentEntity) endProperty.getAssociatedEntity();
                graphParent = (GraphPersistentEntity) startProperty.getAssociatedEntity();

                Object endEntity = entityAccess.getProperty(TO);
                Object startEntity = entityAccess.getProperty(FROM);
                parentId = graphParent.getReflector().getIdentifier(startEntity);
                this.targetIdentifiers.clear();
                this.targetIdentifiers.add( graphChild.getReflector().getIdentifier(endEntity) );
            }
            else {
                // don't do anything for the 'to' end
                return;
            }
        }

        params.put(CypherBuilder.START, parentId);
        params.put(CypherBuilder.END, targetIdentifiers);

        String labelsFrom = graphParent.getLabelsAsString();
        String labelsTo = graphChild.getLabelsAsString();

        final String relMatch;

        if(association instanceof DynamicToOneAssociation) {
            LinkedHashMap<String, String> attrs = new LinkedHashMap<String, String>();
            attrs.put(SOURCE_TYPE, graphParent.getJavaClass().getSimpleName());
            attrs.put(TARGET_TYPE, graphChild.getJavaClass().getSimpleName());
            relMatch = RelationshipUtils.matchForAssociation(association, "r", attrs);
        }
        else if(isRelationshipAssociation) {
            LinkedHashMap<String, Object> attrs = new LinkedHashMap<>();
            GraphPersistentEntity relEntity = (GraphPersistentEntity) getEntity();
            EntityReflector reflector = relEntity.getReflector();
            Neo4jMappingContext mappingContext = (Neo4jMappingContext)relEntity.getMappingContext();

            Neo4jEntityPersister entityPersister = session.getEntityPersister(getEntityAccess().getEntity());
            if(entityPersister.cancelInsert(entity, getEntityAccess())) {
                // operation cancelled, return
                return;
            }

            for (PersistentProperty pp : relEntity.getPersistentProperties()) {
                if(pp instanceof Simple || pp instanceof Basic || pp instanceof TenantId) {
                    String propertyName = pp.getName();
                    if(RelationshipPersistentEntity.TYPE.equals(propertyName)) {
                        continue;
                    }

                    Object v = reflector.getProperty(getNativeEntry(), propertyName);
                    if(v != null) {
                        attrs.put(propertyName, mappingContext.convertToNative(v));
                    }
                }
            }
            Relationship relationship = (Relationship) getEntityAccess().getEntity();
            attrs.putAll( relationship.attributes() );

            params.put("rProps", attrs);
            relMatch = RelationshipUtils.toMatch(association, relationship);
        }
        else {
            relMatch = RelationshipUtils.matchForAssociation(association, "r");
        }

        if(isUpdate &&
           (association instanceof DynamicAssociation ||
            ((association.isBidirectional() && (association instanceof OneToMany)) || (association instanceof OneToOne)) &&
            !RelationshipUtils.useReversedMappingFor(association))) {
            // delete any previous

            StringBuilder cypher = new StringBuilder(CypherBuilder.buildRelationshipMatch(labelsFrom, relMatch, labelsTo));
            Map<String, Object> deleteParams;
            if(association instanceof DynamicToOneAssociation || association instanceof OneToOne) {
                cypher.append(graphChild.formatId(FROM));
                deleteParams = Collections.<String, Object>singletonMap(CypherBuilder.START, Collections.singletonList(parentId));
            }
            else {
                cypher.append(graphChild.formatId(TO));
                deleteParams = Collections.<String, Object>singletonMap(CypherBuilder.START, targetIdentifiers);
            }
            cypher.append(" IN $").append(CypherBuilder.START).append(" DELETE r");
            if(log.isDebugEnabled()) {
                log.debug("DELETE Cypher [{}] for parameters [{}]", cypher, deleteParams);
            }
            boltTransaction.run(cypher.toString(), deleteParams);
        }

        StringBuilder cypherQuery = new StringBuilder(String.format(CypherBuilder.CYPHER_FROM_TO_NODES_MATCH, labelsFrom, labelsTo));

        cypherQuery.append(graphParent.formatId(RelationshipPersistentEntity.FROM))
                   .append(" = $")
                   .append(CypherBuilder.START)
                   .append(" AND ")
                   .append(graphChild.formatId(RelationshipPersistentEntity.TO))
                   .append(" IN $")
                   .append(CypherBuilder.END)
                   .append(" MERGE (from)")
                   .append(relMatch)
                   .append("(to)");
        if(isRelationshipAssociation) {
            cypherQuery.append(" ON CREATE SET r=$rProps");
        }

        String cypher = cypherQuery.toString();

        if (log.isDebugEnabled()) {
            log.debug("MERGE Cypher [{}] for parameters [{}]", cypher, params);
        }
        boltTransaction.run(cypher, params);
    }

}
