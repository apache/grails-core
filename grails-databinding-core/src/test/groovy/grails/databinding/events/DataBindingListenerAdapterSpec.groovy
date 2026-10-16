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

package grails.databinding.events

import grails.databinding.errors.BindingError
import org.grails.databinding.errors.SimpleBindingError
import spock.lang.Specification

class DataBindingListenerAdapterSpec extends Specification {

    void 'the adapter supports every class'() {
        expect:
        new DataBindingListenerAdapter().supports(type)

        where:
        type << [String, Object, Map]
    }

    void 'both beforeBinding overloads allow binding to proceed'() {
        given:
        def adapter = new DataBindingListenerAdapter()

        expect:
        adapter.beforeBinding(new Object(), [:]) == true
        adapter.beforeBinding(new Object(), 'name', 'value', [:]) == true
    }

    void 'afterBinding and bindingError callbacks are no-ops'() {
        given:
        def adapter = new DataBindingListenerAdapter()
        BindingError error = new SimpleBindingError(new Object(), 'name', 'bad', new IllegalStateException('boom'))

        when:
        adapter.afterBinding(new Object(), 'name', [:])
        adapter.afterBinding(new Object(), [:])
        adapter.bindingError(error, [:])

        then:
        noExceptionThrown()
    }
}
