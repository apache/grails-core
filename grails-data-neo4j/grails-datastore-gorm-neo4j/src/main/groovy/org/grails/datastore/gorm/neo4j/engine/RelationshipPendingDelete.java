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

import org.grails.datastore.gorm.neo4j.CypherBuilder;
import org.grails.datastore.gorm.neo4j.GraphPersistentEntity;
import org.grails.datastore.gorm.neo4j.RelationshipPersistentEntity;
import org.grails.datastore.gorm.neo4j.RelationshipUtils;
import org.grails.datastore.mapping.core.impl.PendingOperationAdapter;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.neo4j.driver.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.grails.datastore.gorm.neo4j.engine.RelationshipPendingInsert.FROM;
import static org.grails.datastore.gorm.neo4j.engine.RelationshipPendingInsert.TO;

/**
 * Represents a pending relationship delete
 *
 * @author Stefan
 * @author Graeme Rocher
 */
public class RelationshipPendingDelete extends PendingOperationAdapter<Object, Serializable> {

    private static Logger log = LoggerFactory.getLogger(RelationshipPendingDelete.class);

    private final Transaction boltTransaction;
    private final Association association;
    private final Collection<Serializable> targetIdentifiers;
    private final EntityAccess entityAccess;


    public RelationshipPendingDelete(EntityAccess parent, Association association, Collection<Serializable> pendingInserts, Transaction boltTransaction) {
        super(parent.getPersistentEntity(), (Serializable) parent.getIdentifier(), parent.getEntity());
        this.targetIdentifiers = pendingInserts;
        this.boltTransaction = boltTransaction;
        this.association = association;
        this.entityAccess = parent;
    }

    @Override
    public void run() {
        GraphPersistentEntity graphParent = (GraphPersistentEntity) getEntity();

        Serializable parentId = getNativeKey();
        final boolean isRelationshipAssociation = graphParent.isRelationshipEntity();
        if(isRelationshipAssociation) {
            if(association.getName().equals(FROM)) {
                RelationshipPersistentEntity relEntity = (RelationshipPersistentEntity) graphParent;
                GraphPersistentEntity graphChild = relEntity.getToEntity();
                graphParent = relEntity.getFromEntity();

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

        final Map<String, Object> params = new LinkedHashMap<>(2);

        if(RelationshipUtils.useReversedMappingFor(association)) {
            params.put(GormProperties.IDENTITY, parentId);
        }
        else {
            params.put(CypherBuilder.START, parentId);
            params.put(CypherBuilder.END, targetIdentifiers);
        }


        String cypher = graphParent.formatAssociationDelete(association, entityAccess.getEntity());
        if(cypher != null) {
            if (log.isDebugEnabled()) {
                log.debug("DELETE Cypher [{}] for parameters [{}]", cypher, params);
            }
            boltTransaction.run(cypher, params);
        }
    }
}
