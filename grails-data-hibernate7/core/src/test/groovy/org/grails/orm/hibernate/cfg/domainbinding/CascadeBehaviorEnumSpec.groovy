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

import org.hibernate.MappingException
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior

class CascadeBehaviorEnumSpec extends Specification {

    @Unroll
    void "test isSaveUpdate for #behavior"() {
        expect:
        behavior.isSaveUpdate() == expected

        where:
        behavior            | expected
        CascadeBehavior.ALL | true
        CascadeBehavior.ALL_DELETE_ORPHAN | true
        CascadeBehavior.SAVE_UPDATE      | true
        CascadeBehavior.MERGE            | false
        CascadeBehavior.PERSIST          | false
        CascadeBehavior.DELETE           | false
        CascadeBehavior.LOCK             | false
        CascadeBehavior.EVICT            | false
        CascadeBehavior.REPLICATE        | false
        CascadeBehavior.NONE             | false
    }

    @Unroll
    void "test fromString for #value"() {
        expect:
        CascadeBehavior.fromString(value) == expected

        where:
        value               | expected
        "all"               | CascadeBehavior.ALL
        "all-delete-orphan" | CascadeBehavior.ALL_DELETE_ORPHAN
        "save-update"       | CascadeBehavior.SAVE_UPDATE
        "persist,merge"     | CascadeBehavior.SAVE_UPDATE
        "merge"             | CascadeBehavior.MERGE
        "persist"           | CascadeBehavior.PERSIST
        "none"              | CascadeBehavior.NONE
    }

    void "test fromString with invalid value"() {
        when:
        CascadeBehavior.fromString("invalid")

        then:
        thrown(MappingException)
    }

    @Unroll
    void "test static isSaveUpdate for cascade string: #cascade"() {
        expect:
        CascadeBehavior.isSaveUpdate(cascade) == expected

        where:
        cascade              | expected
        "all"                | true
        "all-delete-orphan"  | true
        "persist,merge"      | true
        "save-update"        | true
        "merge,persist"      | true
        "merge"              | false
        "persist"            | false
        "none"               | false
        "delete"             | false
        "lock"               | false
        "evict"              | false
        "replicate"          | false
        "all,delete"         | true
        "persist,merge,lock" | true
        ""                   | false
        null                 | false
    }
}
