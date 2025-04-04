/* Copyright (C) 2011 SpringSource
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
package org.grails.datastore.gorm.finders;

import grails.gorm.CriteriaBuilder;
import groovy.lang.Closure;

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

    protected final Datastore datastore;

    public AbstractFinder(final Datastore datastore) {
        this.datastore = datastore;
    }

    protected <T> T execute(final SessionCallback<T> callback) {
        if(datastore != null) {
            return DatastoreUtils.execute(datastore, callback);
        }
        else {
            throw new IllegalStateException("Cannot execute session query in stateless mode");
        }
    }

    protected void execute(final VoidSessionCallback callback) {
        if(datastore != null) {
            DatastoreUtils.execute(datastore, callback);
        }
        else {
            throw new IllegalStateException("Cannot execute session query in stateless mode");
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
