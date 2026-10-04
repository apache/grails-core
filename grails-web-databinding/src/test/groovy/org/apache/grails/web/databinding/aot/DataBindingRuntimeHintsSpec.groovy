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
package org.apache.grails.web.databinding.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeHint
import spock.lang.Specification

class DataBindingRuntimeHintsSpec extends Specification {

    void 'the runtime hints register the data binding types that are present'() {
        given:
        RuntimeHints hints = new RuntimeHints()

        when:
        new DataBindingRuntimeHints().registerHints(hints, getClass().classLoader)
        List<TypeHint> typeHints = hints.reflection().typeHints().toList()

        then:
        typeHints*.type*.name.sort() == [
                'grails.databinding.BindingHelper',
                'grails.databinding.DataBindingSource',
                'grails.databinding.converters.ValueConverter',
                'grails.web.databinding.DataBindingUtils',
                'grails.web.databinding.WebDataBinding',
                'java.lang.Class'
        ]
        typeHints.every { TypeHint hint ->
            hint.memberCategories == [MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.INVOKE_PUBLIC_METHODS,
                                      MemberCategory.INVOKE_DECLARED_CONSTRUCTORS] as Set
        }
    }

}
