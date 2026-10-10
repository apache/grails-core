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

import grails.gorm.annotation.Entity

/**
 * Domain classes of the former {@code ColumnBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class CBBook {
    String title
    static hasMany = [authors: CBAuthor]
    static mapping = {
        authors joinTable: [name: "cb_book_authors", key: "book_id", column: "author_id"]
    }
    static constraints = {
        title nullable: false
        authors nullable: true
    }
}

@Entity
class CBAuthor {
    String name
    static constraints = {
        name nullable: false
    }
}

@Entity
class CBNumericBase {
}

@Entity
class CBNumericSub extends CBNumericBase {
    Integer num
    static constraints = {
        num nullable: false
    }
}

@Entity
class CBOwner {
    static hasOne = [pet: CBPet]
}

@Entity
class CBPet {
    String name
    CBOwner owner
}

@Entity
class CBFace {
    CBNose nose
}

@Entity
class CBNose {
    CBFace face
}

@Entity
class CBCircular {
    CBCircular parent
}

@Entity
class CBNullableEntity {
    String nullableProp
    static constraints = {
        nullableProp nullable: true
    }
}

@Entity
class CBUniqueEntity {
    String uniqueProp
    String notUniqueProp
    static mapping = {
        uniqueProp unique: true
        notUniqueProp unique: false
    }
}

@Entity
class CBBaseNonTph {
    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class CBSubNonTph extends CBBaseNonTph {
    String subProp
    static constraints = {
        subProp nullable: false
    }
}

@Entity
class CBByteArrayEntity {
    byte[] data
}

@Entity
class CBUniqueGroupEntity {
    String groupedProp
    static mapping = {
        groupedProp unique: 'group1'
    }
}
