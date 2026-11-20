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

package scaffoldingfields

/**
 * Project domain class - tests many-to-many relationship rendering
 * in scaffolded views (multi-select for hasMany on both sides).
 */
class Project {

    String name
    String code
    Date startDate
    Date endDate
    Boolean active = true

    static hasMany = [employees: Employee]
    static belongsTo = Employee

    static constraints = {
        name blank: false, size: 1..100
        code blank: false, unique: true, size: 1..20, matches: '[A-Z0-9_-]+'
        startDate nullable: false
        endDate nullable: true, validator: { val, obj ->
            if (val && obj.startDate && val < obj.startDate) {
                return 'project.endDate.beforeStartDate'
            }
        }
        active nullable: false
    }

    String toString() {
        "$name ($code)"
    }
}
