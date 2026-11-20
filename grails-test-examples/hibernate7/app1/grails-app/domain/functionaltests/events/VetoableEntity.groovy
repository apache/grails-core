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

package functionaltests.events

/**
 * Domain class demonstrating veto/cancel capability in lifecycle events.
 */
class VetoableEntity {

    String name
    String type
    boolean approved = false
    
    // Control fields for testing
    boolean vetoInsert = false
    boolean vetoUpdate = false
    boolean vetoDelete = false
    
    Date dateCreated
    Date lastUpdated

    static transients = ['vetoInsert', 'vetoUpdate', 'vetoDelete']

    static constraints = {
        name blank: false
        type inList: ['NORMAL', 'RESTRICTED', 'PROTECTED']
    }

    static mapping = {
        table 'vetoable_entities'
    }

    def beforeInsert() {
        if (vetoInsert || type == 'RESTRICTED') {
            return false  // Veto the insert
        }
    }

    def beforeUpdate() {
        if (vetoUpdate || (type == 'PROTECTED' && !approved)) {
            return false  // Veto the update
        }
    }

    def beforeDelete() {
        if (vetoDelete || type == 'PROTECTED') {
            return false  // Veto the delete
        }
    }
}
