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

package org.grails.datastore.gorm.mongo.transactions

import groovy.transform.CompileStatic

/**
 * Thread-local transaction context for MongoDB-specific rollback-aware execution.
 */
@CompileStatic
class MongoTransactionContext {

    private static final ThreadLocal<Boolean> ROLLBACK_AWARE = new ThreadLocal<>()

    static boolean isRollbackAwareActive() {
        Boolean.TRUE == ROLLBACK_AWARE.get()
    }

    static <T> T withRollbackAware(Closure<T> work) {
        Boolean previous = ROLLBACK_AWARE.get()
        ROLLBACK_AWARE.set(Boolean.TRUE)
        try {
            return work.call()
        } finally {
            if (previous == null) {
                ROLLBACK_AWARE.remove()
            } else {
                ROLLBACK_AWARE.set(previous)
            }
        }
    }
}
