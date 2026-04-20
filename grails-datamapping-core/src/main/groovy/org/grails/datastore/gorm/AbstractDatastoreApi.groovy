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
package org.grails.datastore.gorm

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.SessionCallback
import org.grails.datastore.mapping.core.VoidSessionCallback

/**
 * @author Burt Beckwith
 */
@CompileStatic
abstract class AbstractDatastoreApi {

    protected Datastore datastore

    protected AbstractDatastoreApi(Datastore datastore) {
        this.datastore = datastore
    }

    protected <T> T execute(SessionCallback<T> callback) {
        Datastore ds = getDatastore()
        DatastoreUtils.execute(ds, callback)
    }

    protected void execute(VoidSessionCallback callback) {
        Datastore ds = getDatastore()
        DatastoreUtils.execute(ds, callback)
    }

    Datastore getDatastore() {
        if (this.datastore == null) {
            // 1. Check for Thread-Local override (Highest priority for TCK/Unit Tests)
            Datastore override = GormEnhancer.getThreadLocalDatastore()
            if (override != null) {
                return override
            }

            // 2. Resolve from the currently bound session (Dynamic context)
            if (this instanceof AbstractGormApi) {
                Class persistentClass = ((AbstractGormApi) this).persistentClass
                return GormEnhancer.findDatastore(persistentClass)
            }
            throw new IllegalStateException('No datastore configured in stateless mode')
        }
        return this.datastore
    }
}
