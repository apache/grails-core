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
package grails.unbootable

import grails.gorm.annotation.Entity

/**
 * Domain classes the domain binder cannot boot, kept outside the packages the differential spec scans. The specs of the domain
 * generator build their mapping model without binding them, to show that the generator rejects them by name.
 */
@Entity
class UnbootableComposite implements Serializable {

    String code
    String region

    static mapping = {
        id composite: ['code', 'region']
    }
}

@Entity
class UnbootableJoinToComposite {

    Set<UnbootableComposite> targets

    static hasMany = [targets: UnbootableComposite]
}

@Entity
class UnbootableMmComposite implements Serializable {

    String a
    String b
    Set<UnbootableMmOther> others

    static hasMany = [others: UnbootableMmOther]

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class UnbootableMmOther {

    Set<UnbootableMmComposite> composites

    static hasMany = [composites: UnbootableMmComposite]
    static belongsTo = [UnbootableMmComposite]
}

@Entity
class UnbootableTarget {

    String label
}

@Entity
class UnbootableParts implements Serializable {

    UnbootableTarget owner
    String name

    static mapping = {
        id composite: ['name', 'owner']
    }
}

@Entity
class UnbootableRefToParts {

    UnbootableParts target
}

@Entity
class UnbootableFlat implements Serializable {

    String a
    String b

    static mapping = {
        id composite: ['a', 'b']
    }
}

@Entity
class UnbootableMiddle implements Serializable {

    UnbootableFlat flat
    String name

    static mapping = {
        id composite: ['flat', 'name']
    }
}

@Entity
class UnbootableTop implements Serializable {

    UnbootableMiddle middle
    String name

    static mapping = {
        id composite: ['middle', 'name']
    }
}

@Entity
class UnbootableRefToTop {

    UnbootableTop target
}

@Entity
class UnbootableJoinedParent implements Serializable {

    String a
    String b

    static mapping = {
        tablePerHierarchy false
        id composite: ['a', 'b']
    }
}

@Entity
class UnbootableJoinedChild extends UnbootableJoinedParent {

    String c
}
