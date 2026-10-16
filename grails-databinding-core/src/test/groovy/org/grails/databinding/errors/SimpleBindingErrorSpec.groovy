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

package org.grails.databinding.errors

import spock.lang.Specification

class SimpleBindingErrorSpec extends Specification {

    void 'the constructor arguments are exposed through the BindingError accessors'() {
        given:
        def target = new Object()
        def cause = new NumberFormatException('not a number')

        when:
        def error = new SimpleBindingError(target, 'age', 'abc', cause)

        then:
        error.object.is(target)
        error.propertyName == 'age'
        error.rejectedValue == 'abc'
        error.cause.is(cause)
    }

    void 'a null rejected value and cause are preserved'() {
        when:
        def error = new SimpleBindingError('target', 'name', null, null)

        then:
        error.rejectedValue == null
        error.cause == null
    }
}
