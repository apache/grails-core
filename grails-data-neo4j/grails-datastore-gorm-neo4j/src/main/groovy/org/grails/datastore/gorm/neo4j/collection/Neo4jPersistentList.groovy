
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
import org.grails.datastore.gorm.neo4j.engine.Neo4jAssociationQueryExecutor
import org.grails.datastore.mapping.collection.PersistentList
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ToMany
/**
 * Neo4j version of the {@link PersistentList} class
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
class Neo4jPersistentList extends PersistentList {

    protected transient final EntityAccess parentAccess
    protected transient final Association association
    protected transient final @Delegate GraphAdapter graphAdapter

    Neo4jPersistentList(Collection keys, Neo4jSession session, EntityAccess parentAccess, ToMany association) {
        super(keys, association.associatedEntity.javaClass, session)
        this.parentAccess = parentAccess
        this.association = association
        this.graphAdapter = new GraphAdapter(session, parentAccess, association)
        setProxyEntities(association.isLazy())
    }

    Neo4jPersistentList(Serializable associationKey, Neo4jSession session, EntityAccess parentAccess, ToMany association) {
        super(associationKey, session, new Neo4jAssociationQueryExecutor(session, association, association.isLazy()))
        this.parentAccess = parentAccess
        this.association = association
        this.graphAdapter = new GraphAdapter(session, parentAccess, association)
        setProxyEntities(association.isLazy())
    }

    @Override
    boolean addAll(Collection c) {
        def added = super.addAll(c)
        if (added) {
            for (o in c) {
                adaptGraphUponAdd(o, currentlyInitializing())
            }
        }

        return added
    }

    @Override
    boolean add(Object o) {
        def added = super.add(o)
        if(added) {
            adaptGraphUponAdd(o, currentlyInitializing())
        }

        return added
    }

    @Override
    boolean removeAll(Collection c) {
        def removed = super.removeAll(c)
        if(removed) {
            for(o in c) {
                adaptGraphUponRemove(o, currentlyInitializing())
            }
        }
        return removed
    }

    @Override
    boolean remove(Object o) {
        def removed = super.remove(o)
        if(removed) {
            adaptGraphUponRemove(o, currentlyInitializing())
        }
        return removed
    }

    @Override
    boolean retainAll(Collection c) {
        return super.retainAll(c)
    }

    @Override
    Object[] toArray(Object[] a) {
        return super.toArray(a)
    }

    @Override
    boolean containsAll(Collection c) {
        return super.containsAll(c)
    }

    @Override
    void markDirty() {
        if (!currentlyInitializing()) {
            ((DirtyCheckable) parentAccess.entity).markDirty(association.getName())
            super.markDirty()
        }
    }
}
