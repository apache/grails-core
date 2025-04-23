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

package org.grails.datastore.mapping.reflect

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification

/**
 * Created by graemerocher on 19/04/2017.
 */
class AstUtilsSpec extends Specification {

    void "test implements interface"() {
        given:
        ClassNode node = ClassHelper.make("Test")
        def itfc = ClassHelper.make("ITest")
        node.addInterface(itfc)

        expect:
        AstUtils.implementsInterface(node, itfc)
        AstUtils.implementsInterface(node, itfc.name)
        !AstUtils.implementsInterface(node, "Another")
    }
}
