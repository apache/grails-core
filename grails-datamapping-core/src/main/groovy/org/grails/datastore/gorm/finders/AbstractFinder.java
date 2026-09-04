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
package org.grails.datastore.gorm.finders;

import groovy.lang.Closure;

import grails.gorm.CriteriaBuilder;
import org.grails.datastore.gorm.DatastoreResolver;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.core.DatastoreUtils;
import org.grails.datastore.mapping.core.SessionCallback;
import org.grails.datastore.mapping.core.VoidSessionCallback;
import org.grails.datastore.mapping.query.Query;

/**
 * Abstract base class for finders.
 *
 * @author Burt Beckwith
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractFinder implements FinderMethod {

    protected DatastoreResolver datastoreResolver;
    protected Datastore datastore;

    public AbstractFinder(final Datastore datastore) {
        this.datastore = datastore;
    }

    public AbstractFinder(final DatastoreResolver datastoreResolver) {
        this.datastoreResolver = datastoreResolver;
    }

    protected Datastore getDatastore() {
        if (datastoreResolver != null) {
            return datastoreResolver.resolve();
        }
        return datastore;
    }

    protected <T> T execute(final SessionCallback<T> callback) {
        Datastore ds = getDatastore();
        if (ds != null) {
            return DatastoreUtils.execute(ds, callback);
        }
        else {
            throw new IllegalStateException("Cannot execute session query with null datastore");
        }
    }

    protected void execute(final VoidSessionCallback callback) {
        Datastore ds = getDatastore();
        if (ds != null) {
            DatastoreUtils.execute(ds, callback);
        }
        else {
            throw new IllegalStateException("Cannot execute session query with null datastore");
        }

    }

    protected void applyAdditionalCriteria(Query query, Closure additionalCriteria) {
        if (additionalCriteria == null) {
            return;
        }

        CriteriaBuilder builder = new CriteriaBuilder(query.getEntity().getJavaClass(), query.getSession(), query);
        builder.build(additionalCriteria);
    }
}
