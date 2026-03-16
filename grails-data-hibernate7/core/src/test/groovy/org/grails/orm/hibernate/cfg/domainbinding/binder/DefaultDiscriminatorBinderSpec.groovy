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

package org.grails.orm.hibernate.cfg.domainbinding.binder

import spock.lang.Specification
import spock.lang.Subject

/**
 * Tests for DefaultDiscriminatorBinder focusing on logic rather than Hibernate integration
 * since many Hibernate 7 classes are sealed and cannot be mocked.
 */
class DefaultDiscriminatorBinderSpec extends Specification {

    @Subject
    DefaultDiscriminatorBinder binder

    SimpleValueColumnBinder simpleValueColumnBinder = Mock()

    def setup() {
        binder = new DefaultDiscriminatorBinder(simpleValueColumnBinder)
    }

    def "test constructor sets dependencies correctly"() {
        expect:
        binder.simpleValueColumnBinder == simpleValueColumnBinder
    }
}
