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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity

/**
 * Domain classes of the former {@code BasicCollectionElementBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
enum BCEBStatus { ACTIVE, INACTIVE }

@Entity
class BCEBAuthor {
    Long id
    java.util.Set<String> tags
    java.util.Set<BCEBStatus> statuses
    static hasMany = [tags: String, statuses: BCEBStatus]
}

@Entity
class BCEBCustom {
    Long id
    java.util.Set<String> flags
    static hasMany = [flags: String]
    static mapping = {
        // Targets the joinColumnMappingOptional branch (Line 74)
        flags joinTable: [column: '`flag_identifier`']
    }
}

@Entity
class BCEBReserved {
    Long id
    java.util.Set<String> group // 'group' is a SQL reserved word
    static hasMany = [group: String]
}

@Entity
class BCEBDefault {
    Long id
    java.util.Set<String> tags
    static hasMany = [tags: String]
}

@Entity
class BCEBExplicit {
    Long id
    java.util.Set<String> flags
    static hasMany = [flags: String]
    static mapping = {
        flags joinTable: [column: "custom_flag_col"]
    }
}

@Entity
class BCEBPath1 { // Explicit Mapping
    Long id
    java.util.Set<String> flags
    static hasMany = [flags: String]
    static mapping = {
        flags joinTable: [column: "explicit_col"]
    }
}

@Entity
class BCEBPath2 { // Default Enum
    Long id
    java.util.Set<BCEBStatus> statuses
    static hasMany = [statuses: BCEBStatus]
}

@Entity
class BCEBPath3 { // Default Scalar
    Long id
    java.util.Set<String> tags
    static hasMany = [tags: String]
}
