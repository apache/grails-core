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
import groovy.transform.PackageScope
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.neo4j.Neo4jSession
import org.grails.datastore.gorm.neo4j.RelationshipUtils
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.proxy.ProxyFactory
import org.grails.datastore.mapping.reflect.EntityReflector


/**
 * Helps to Adapt a collection to the Neo4j graph
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@PackageScope
class GraphAdapter {

    final EntityAccess parentAccess
    final Neo4jSession session
    final Association association
    final boolean reversed
    final String relType
    final Class childType

    GraphAdapter(Neo4jSession session, EntityAccess parentAccess, Association association) {
        this.association = association
        this.session = session
        this.parentAccess = parentAccess
        reversed = RelationshipUtils.useReversedMappingFor(association)
        relType = RelationshipUtils.relationshipTypeUsedFor(association)
        childType = association.associatedEntity.javaClass
    }

    void adaptGraphUponRemove(Object o, boolean currentlyInitializing = false) {
        if(currentlyInitializing) return

        Neo4jSession session = (Neo4jSession)this.session
        def childAccess = session.mappingContext.getEntityReflector(association.getAssociatedEntity())

        def proxyFactory = session.getMappingContext().getProxyFactory()
        Serializable id
        if (proxyFactory.isProxy(o)) {
            id = proxyFactory.getIdentifier(o)
        }
        else {
            id = (Serializable) childAccess.getIdentifier(o)
        }
        if(association.isOrphanRemoval()) {
            session.delete(o)
        }
        else if (!reversed && id != null) {
            session.addPendingRelationshipDelete((Serializable)parentAccess.getIdentifier(), association, id)
        }

    }

    void adaptGraphUponAdd(Object t, boolean currentlyInitializing = false) {
        def proxyFactory = session.getMappingContext().getProxyFactory()
        Neo4jSession session = (Neo4jSession)this.session
        if(currentlyInitializing) {
            // if the association is initializing then replace parent entities with non proxied version to prevent N+1 problem
            if (association.isBidirectional() && !proxyFactory.isProxy(t)) {
                def inverseSide = association.inverseSide
                if(inverseSide instanceof ToOne) {
                    EntityReflector target = session.mappingContext.getEntityReflector(association.getAssociatedEntity())
                    target.setProperty( t, inverseSide.name, parentAccess.entity )
                }
            }
        }
        else {

            if (proxyFactory.isProxy(t)) {
                if ( !proxyFactory.isInitialized(t) ) return
                if ( !childType.isInstance(t) ) return
            }
            EntityReflector target = session.mappingContext.getEntityReflector(association.getAssociatedEntity())

            if (association.isBidirectional()) {
                if (association instanceof ManyToMany) {
                    Collection coll = (Collection) target.getProperty(t, association.getReferencedPropertyName());
                    coll.add(parentAccess.entity);
                } else {
                    target.setProperty(t, association.getReferencedPropertyName(), parentAccess.entity);
                }
            }


            def identifier = target.getIdentifier(t)
            if (identifier == null) { // non-persistent instance
                identifier = session.persist(t);
            }

            if (!reversed && identifier != null) { // prevent duplicated rels
                session.addPendingRelationshipInsert((Serializable)parentAccess.getIdentifier(), association, identifier)
            }
        }

    }
}
