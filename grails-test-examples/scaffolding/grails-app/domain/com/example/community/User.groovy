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
package com.example.community

import grails.gorm.annotation.CreatedBy
import grails.gorm.annotation.CreatedDate
import grails.gorm.annotation.LastModifiedBy
import grails.gorm.annotation.LastModifiedDate
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

import java.time.LocalDateTime

@Entity(name = "CommunityUser")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id
    String firstName
    String lastName

    @CreatedDate LocalDateTime created
    @LastModifiedDate LocalDateTime modified
    @CreatedBy String createdBy
    @LastModifiedBy String modifiedBy

    static constraints = {
        firstName blank: false
        lastName blank: false
        createdBy nullable: true
        modifiedBy nullable: true
    }

    static mapping = {
        table 'community_users'
    }
}
