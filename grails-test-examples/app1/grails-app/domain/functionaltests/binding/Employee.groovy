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

package functionaltests.binding

import grails.databinding.BindInitializer
import grails.databinding.BindUsing
import grails.databinding.BindingFormat

/**
 * Employee domain class demonstrating advanced data binding features.
 */
class Employee {
    String firstName
    String lastName
    
    @BindUsing({ obj, source ->
        source['email']?.toLowerCase()?.trim()
    })
    String email
    
    @BindingFormat('MMddyyyy')
    Date hireDate
    
    @BindingFormat('yyyy-MM-dd')
    Date birthDate
    
    Integer salary
    
    Address homeAddress
    Address workAddress
    
    static constraints = {
        firstName nullable: true
        lastName nullable: true
        email nullable: true
        hireDate nullable: true
        birthDate nullable: true
        salary nullable: true
        homeAddress nullable: true
        workAddress nullable: true
    }
}
