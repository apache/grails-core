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
 * Domain classes of the former {@code UnidirectionalOneToManyInverseValuesBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class UOTMBook {
    Long id
    String title
}

@Entity
class UOTMAuthor {
    Long id
    String name
    Set<UOTMBook> books
    static hasMany = [books: UOTMBook]
}

@Entity
class UOTMAuthorCustom {
    Long id
    String name
    Set<UOTMBook> books
    static hasMany = [books: UOTMBook]
    static mapping = {
        books ignoreNotFound: true, fetch: 'join', lazy: false
    }
}
