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
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Association


/**
 * Extended result list aware of the parent entity and association to prevent N+1 queries
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class Neo4jAssociationResultList extends Neo4jResultList {

    final EntityAccess parent
    final Association association
    final Neo4jSession session

    Neo4jAssociationResultList(EntityAccess parent, Association association, Integer size, Iterator<Object> cursor, Neo4jSession session) {
        super(0, size, cursor, session.getEntityPersister(association.getAssociatedEntity()))
        this.parent = parent
        this.association = association
        this.session = session
        if(association.isBidirectional()) {
            setInitializedAssociations(Collections.<Association,Object>singletonMap(association.inverseSide, parent.entity))
        }
    }

}
