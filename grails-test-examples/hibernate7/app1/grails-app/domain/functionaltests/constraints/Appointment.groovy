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

package functionaltests.constraints

/**
 * Domain class demonstrating date and time constraints:
 * - Date range validation
 * - Future/past date validation
 * - Business hours validation
 * - Duration constraints
 */
class Appointment {

    String title
    String description
    Date startDate
    Date endDate
    Integer durationMinutes
    String location
    String attendeeEmail
    String status
    Integer priority
    Date reminderDate

    Date dateCreated
    Date lastUpdated

    static constraints = {
        title blank: false, size: 3..100

        description nullable: true, maxSize: 1000

        // Start date must be in the future
        startDate validator: { val ->
            if (!val) return true
            if (val <= new Date()) {
                return 'appointment.startDate.mustBeFuture'
            }
        }

        // End date must be after start date
        endDate validator: { val, obj ->
            if (!val || !obj.startDate) return true
            if (val <= obj.startDate) {
                return 'appointment.endDate.mustBeAfterStart'
            }
            // Maximum appointment duration: 8 hours
            def diffHours = (val.time - obj.startDate.time) / (1000 * 60 * 60)
            if (diffHours > 8) {
                return 'appointment.endDate.tooLong'
            }
        }

        // Duration must match start/end dates
        durationMinutes nullable: true, min: 15, max: 480, validator: { val, obj ->
            if (val == null || !obj.startDate || !obj.endDate) return true
            def calculatedMinutes = (obj.endDate.time - obj.startDate.time) / (1000 * 60)
            if (Math.abs(val - calculatedMinutes) > 1) {  // Allow 1 minute tolerance
                return 'appointment.durationMinutes.doesNotMatch'
            }
        }

        location nullable: true, size: 0..200

        attendeeEmail email: true, nullable: true

        // Status with valid transitions
        status inList: ['Scheduled', 'Confirmed', 'InProgress', 'Completed', 'Cancelled'], 
               validator: { val, obj ->
            // New appointments must start as Scheduled
            if (obj.id == null && val != 'Scheduled') {
                return 'appointment.status.mustStartAsScheduled'
            }
        }

        // Priority 1-5
        priority range: 1..5, nullable: true

        // Reminder must be before start date but not too far in advance
        reminderDate nullable: true, validator: { val, obj ->
            if (!val || !obj.startDate) return true
            if (val >= obj.startDate) {
                return 'appointment.reminderDate.mustBeBeforeStart'
            }
            // Reminder cannot be more than 7 days before
            def diffDays = (obj.startDate.time - val.time) / (1000 * 60 * 60 * 24)
            if (diffDays > 7) {
                return 'appointment.reminderDate.tooEarly'
            }
        }
    }
}
