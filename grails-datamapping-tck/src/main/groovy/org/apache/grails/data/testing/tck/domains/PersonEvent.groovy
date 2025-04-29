/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.grails.data.testing.tck.domains

import grails.persistence.Entity

@Entity
class PersonEvent implements Serializable {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

    def personService

    static STORE_INITIAL = [
            beforeDelete: 0, afterDelete: 0,
            beforeUpdate: 0, afterUpdate: 0,
            beforeInsert: 0, afterInsert: 0,
            beforeLoad:   0, afterLoad:   0]

    static STORE = [:] + STORE_INITIAL

    static void resetStore() {
        STORE = [:] + STORE_INITIAL
    }

    def beforeDelete() {
        if (name == "DontDelete") {
            return false
        }
        STORE.beforeDelete++
    }

    void afterDelete() {
        STORE.afterDelete++
    }

    def beforeUpdate() {
        if (name == "Bad") {
            return false
        }
        STORE.beforeUpdate++
    }

    void afterUpdate() {
        STORE.afterUpdate++
    }

    def beforeInsert() {
        if (name == "Bad") {
            return false
        }
        STORE.beforeInsert++
    }

    void afterInsert() {
        STORE.afterInsert++
    }

    void beforeLoad() {
        STORE.beforeLoad++
    }

    void afterLoad() {
        STORE.afterLoad++
    }
}

