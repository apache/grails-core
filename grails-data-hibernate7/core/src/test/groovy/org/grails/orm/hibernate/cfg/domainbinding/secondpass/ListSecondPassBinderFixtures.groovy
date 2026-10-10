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

import grails.persistence.Entity

/**
 * Domain classes of the former {@code ListSecondPassBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@Entity
class LSBCustomIndex {
    Long id
    java.util.List<String> items
    static hasMany = [items: String]
    static mapping = {
        items index: [column: "my_index_col", type: "long"]
    }
}

@Entity
class LSBCircular {
    Long id
    LSBCircular parent
    java.util.List<LSBCircular> children
    static hasMany = [children: LSBCircular]
    static belongsTo = [parent: LSBCircular]
}

@Entity
class LSBAuthor {
    Long id
    java.util.List<LSBBook> books
    static hasMany = [books: LSBBook]
}

@Entity
class LSBBook {
    Long id
    LSBAuthor author
    static belongsTo = [author: LSBAuthor]
}

@Entity
class LSBManyToManyA {
    Long id
    java.util.List<LSBManyToManyB> others
    static hasMany = [others: LSBManyToManyB]
}

@Entity
class LSBManyToManyB {
    Long id
    java.util.List<LSBManyToManyA> owners
    static hasMany = [owners: LSBManyToManyA]
    static belongsTo = LSBManyToManyA
}

@Entity
class LSBCompositeIdOwner {
    Long id
    java.util.List<LSBCompositeIdItem> items
    static hasMany = [items: LSBCompositeIdItem]
}

@Entity
class LSBCompositeIdItem implements Serializable {
    LSBCompositeIdOwner owner
    String name
    static belongsTo = [owner: LSBCompositeIdOwner]
    static mapping = {
        id composite: ['owner', 'name']
    }
}
