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

package grails.databinding

import spock.lang.Specification

class FrameworkPropertyNamesSpec extends Specification {

    void 'intrinsic runtime properties are the expected fixed set'() {
        expect:
        FrameworkPropertyNames.INTRINSIC_RUNTIME_PROPERTIES == [
                'class', 'classLoader', 'protectionDomain', 'metaClass', 'metaPropertyValues', 'properties'
        ] as Set<String>
    }

    void 'grails managed properties are the expected fixed set'() {
        expect:
        FrameworkPropertyNames.GRAILS_MANAGED_PROPERTIES == [
                'errors', 'id', 'version', 'dateCreated', 'lastUpdated'
        ] as Set<String>
    }

    void 'framework managed properties is the union of intrinsic runtime and grails managed properties'() {
        expect:
        FrameworkPropertyNames.FRAMEWORK_MANAGED_PROPERTIES ==
                FrameworkPropertyNames.INTRINSIC_RUNTIME_PROPERTIES + FrameworkPropertyNames.GRAILS_MANAGED_PROPERTIES
    }
}
