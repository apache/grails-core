/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.collection;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.model.types.Association;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A lazy loaded sorted set.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PersistentSortedSet extends AbstractPersistentCollection implements SortedSet {

    public PersistentSortedSet(Association association, Serializable associationKey, Session session) {
        super(association, associationKey, session, new TreeSet());
    }

    public PersistentSortedSet(Class childType, Session session, SortedSet collection) {
        super(childType, session, collection);
    }

    public PersistentSortedSet(Collection keys, Class childType, Session session) {
        super(keys, childType, session, new TreeSet());
    }

    public PersistentSortedSet(Serializable associationKey, Session session, AssociationQueryExecutor indexer) {
        super(associationKey, session, indexer, new TreeSet());
    }

    public Comparator comparator() {
        return getSortedSet().comparator();
    }

    private SortedSet getSortedSet() {
        initialize();
        return ((SortedSet) collection);
    }

    public SortedSet subSet(Object o, Object o1) {
        return getSortedSet().subSet(o, o1);
    }

    public SortedSet headSet(Object o) {
        return getSortedSet().headSet(o);
    }

    public SortedSet tailSet(Object o) {
        return getSortedSet().tailSet(o);
    }

    public Object first() {
        return getSortedSet().first();
    }

    public Object last() {
        return getSortedSet().last();
    }
}

