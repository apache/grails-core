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

package org.grails.orm.hibernate.cfg.domainbinding

import grails.persistence.Entity

/**
 * Domain classes of the former {@code PropertyBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class PBEntity {
    Long id
    String name
    PBAuthor author
    PBAuthor eagerAuthor

    static mapping = {
        name nullable: false
        eagerAuthor lazy: false
    }
}

@Entity
class PBWriteEntity {
    Long id
    String writable
    String insertOnly
    String updateOnly
    String readOnly

    static mapping = {
        insertOnly updatable: false
        updateOnly insertable: false
        readOnly insertable: false, updatable: false
    }
}

@Entity
class PBAuthor {
    Long id
    String name
}

enum PBStatus { ACTIVE, INACTIVE }

@Entity
class PBCascadeEntity {
    Long id
    static hasMany = [tags: String, statuses: PBStatus]
}
