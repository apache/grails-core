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
package grails.gorm.tests

import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A many-to-many where neither side declares {@code belongsTo} has no owning side, so the relationship is never
 * stored. A warning at startup points this out.
 */
class ManyToManyWithoutOwnerWarningSpec extends Specification {

    private static final String WARNING = 'declares belongsTo, so the relationship is not stored'

    void 'a many-to-many with no belongsTo on either side logs one warning'() {
        when:
        var output = stderrWhile {
            new HibernateDatastore(WarnNoOwnerLeft, WarnNoOwnerRight).close()
        }

        then:
        output.readLines().count { it.contains(WARNING) } == 1
        output.contains('[grails.gorm.tests.WarnNoOwnerLeft.rights] and [grails.gorm.tests.WarnNoOwnerRight.lefts]')
        output.contains('for example in WarnNoOwnerRight: static belongsTo = WarnNoOwnerLeft')
    }

    void 'a many-to-many with belongsTo on one side logs no warning'() {
        when:
        var output = stderrWhile {
            new HibernateDatastore(WarnOwnedLeft, WarnOwnerRight).close()
        }

        then:
        !output.contains(WARNING)
    }

    void 'a self-referencing many-to-many logs no warning'() {
        when:
        var output = stderrWhile {
            new HibernateDatastore(WarnSelfReferencing).close()
        }

        then:
        !output.contains(WARNING)
    }

    void 'a unidirectional one-to-many and a basic collection log no warning'() {
        when:
        var output = stderrWhile {
            new HibernateDatastore(WarnParent, WarnChild).close()
        }

        then:
        !output.contains(WARNING)
    }

    private static String stderrWhile(Closure<?> work) {
        var original = System.err
        var buffer = new ByteArrayOutputStream()
        System.setErr(new PrintStream(buffer, true))
        try {
            work()
        }
        finally {
            System.setErr(original)
        }
        buffer.toString()
    }
}

@Entity
class WarnNoOwnerLeft {

    String name
    Set<WarnNoOwnerRight> rights

    static hasMany = [rights: WarnNoOwnerRight]
}

@Entity
class WarnNoOwnerRight {

    String name
    Set<WarnNoOwnerLeft> lefts

    static hasMany = [lefts: WarnNoOwnerLeft]
}

@Entity
class WarnOwnedLeft {

    String name
    Set<WarnOwnerRight> owners

    static hasMany = [owners: WarnOwnerRight]
    static belongsTo = WarnOwnerRight
}

@Entity
class WarnOwnerRight {

    String name
    Set<WarnOwnedLeft> lefts

    static hasMany = [lefts: WarnOwnedLeft]
}

@Entity
class WarnSelfReferencing {

    String name
    Set<WarnSelfReferencing> friends

    static hasMany = [friends: WarnSelfReferencing]
}

@Entity
class WarnParent {

    String name
    Set<WarnChild> children
    Set<String> labels

    static hasMany = [children: WarnChild, labels: String]
}

@Entity
class WarnChild {

    String name
}
