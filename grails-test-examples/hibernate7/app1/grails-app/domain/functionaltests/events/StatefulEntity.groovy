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
 * Domain class demonstrating event-driven state machine.
 */
class StatefulEntity {

    String name
    String state = 'PENDING'
    String previousState
    
    // State transition tracking (transient - not persisted)
    static transients = ['stateHistory']
    List<String> stateHistory = []
    
    Integer transitionCount = 0
    Date stateChangedAt
    
    Date dateCreated
    Date lastUpdated

    static constraints = {
        name blank: false
        state inList: ['PENDING', 'SUBMITTED', 'APPROVED', 'REJECTED', 'COMPLETED']
        previousState nullable: true
        transitionCount nullable: true
        stateChangedAt nullable: true
    }

    static mapping = {
        table 'stateful_entities'
    }

    def beforeUpdate() {
        // Track state transitions
        if (isDirty('state')) {
            previousState = getPersistentValue('state') as String
            stateHistory << "From ${previousState} to ${state}"
            transitionCount++
            stateChangedAt = new Date()
        }
    }
    
    // State validation - prevent invalid transitions
    boolean canTransitionTo(String newState) {
        def validTransitions = [
            'PENDING': ['SUBMITTED'],
            'SUBMITTED': ['APPROVED', 'REJECTED'],
            'APPROVED': ['COMPLETED'],
            'REJECTED': ['PENDING'],
            'COMPLETED': []
        ]
        return newState in validTransitions[state]
    }
    
    void transitionTo(String newState) {
        if (!canTransitionTo(newState)) {
            throw new IllegalStateException("Cannot transition from ${state} to ${newState}")
        }
        state = newState
    }
}
