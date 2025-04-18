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
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingSet
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.types.Association
/**
 * A {@link DirtyCheckingSet} for Neo4j
 *
 *
 * @author Graeme Rocher
 */
@CompileStatic
class Neo4jSet extends DirtyCheckingSet {
    final transient Association association
    final transient Neo4jSession session

    protected final transient @Delegate GraphAdapter graphAdapter

    Neo4jSet(EntityAccess parentAccess, Association association, Set delegate, Neo4jSession session) {
        super(delegate, (DirtyCheckable)parentAccess.getEntity(), association.getName())
        this.association = association
        this.session = session
        this.graphAdapter = new GraphAdapter(session, parentAccess, association)
    }

    @Override
    boolean add(Object o) {

        def added = super.add(o)
        if(added) {
            adaptGraphUponAdd(o)
        }
        return added
    }

    @Override
    boolean addAll(Collection c) {
        def added = super.addAll(c)
        if(added) {
            for( o in c ) {
                adaptGraphUponAdd(o)
            }
        }

        return added
    }

    @Override
    boolean removeAll(Collection c) {
        def removed = super.removeAll(c)
        if(removed) {
            for(o in c) {
                adaptGraphUponRemove(o)
            }
        }
        return removed
    }

    @Override
    boolean remove(Object o) {
        def removed = super.remove(o)
        if(removed) {
            adaptGraphUponRemove(o)
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

}
