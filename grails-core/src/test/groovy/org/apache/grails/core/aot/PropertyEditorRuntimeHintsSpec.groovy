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
package org.apache.grails.core.aot

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates
import spock.lang.Specification

import org.grails.beans.support.PropertiesEditor
import org.grails.core.support.ClassEditor

/**
 * Covers the editors the core plugin installs being creatable in an image. Spring creates one of each
 * through its constructor for every bean it sets properties on.
 */
class PropertyEditorRuntimeHintsSpec extends Specification {

    RuntimeHints hints = new RuntimeHints()

    void setup() {
        new PropertyEditorRuntimeHints().registerHints(hints, getClass().classLoader)
    }

    void 'the editors the core plugin registers by class can be created'() {
        expect: 'an image without them could not create the first bean Spring configured'
            [ClassEditor, PropertiesEditor].every {
                RuntimeHintsPredicates.reflection().onType(it)
                        .withMemberCategories(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS).test(hints)
            }
    }
}
