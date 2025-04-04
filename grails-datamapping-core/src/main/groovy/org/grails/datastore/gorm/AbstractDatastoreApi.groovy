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
package org.grails.datastore.gorm

import groovy.transform.CompileStatic;

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.DatastoreUtils
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
        if(datastore == null) {
            throw new IllegalStateException("Cannot execute session callback with null datastore")
        }
        DatastoreUtils.execute datastore, callback
    }

    protected void execute(VoidSessionCallback callback) {
        if(datastore == null) {
            throw new IllegalStateException("Cannot execute session callback with null datastore")
        }
        DatastoreUtils.execute datastore, callback
    }

    Datastore getDatastore() {
        if(datastore == null) {
            throw new IllegalStateException("No datastore configured in stateless mode")
        }
        return datastore
    }
}
