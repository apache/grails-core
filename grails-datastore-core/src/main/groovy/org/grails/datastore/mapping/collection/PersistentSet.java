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

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.model.types.Association;

/**
 * A lazy loaded set.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class PersistentSet extends AbstractPersistentCollection implements Set {

    public PersistentSet(Association association, Serializable associationKey, Session session) {
        super(association, associationKey, session, createCollection());
    }

    public PersistentSet(Association association, Serializable associationKey, Session session, Set collection) {
        super(association, associationKey, session, collection);
    }

    public PersistentSet(Class childType, Session session, Collection collection) {
        super(childType, session, collection);
    }

    public PersistentSet(Collection keys, Class childType, Session session) {
        super(keys, childType, session, createCollection());
    }

    protected static HashSet createCollection() {
        return new HashSet();
    }

    public PersistentSet(Serializable associationKey, Session session, AssociationQueryExecutor indexer) {
        super(associationKey, session, indexer, createCollection());
    }

}
