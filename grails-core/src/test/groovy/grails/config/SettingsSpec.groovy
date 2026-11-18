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
package grails.config

import spock.lang.Specification

import org.grails.compiler.injection.StaticInitializerCalls

/**
 * Covers the one computed constant in {@link Settings}, which is evaluated as an application starts.
 */
class SettingsSpec extends Specification {

    void 'the default encoding is the platform file encoding'() {
        expect:
            Settings.DEFAULT_ENCODING == System.getProperty('file.encoding', 'UTF-8')
    }

    void 'the default encoding is read without dynamic dispatch'() {
        when:
            def calls = StaticInitializerCalls.of(Settings)

        then: 'System.getProperty is called directly'
            calls.contains('INVOKESTATIC java/lang/System.getProperty')

        and: 'nothing goes through a metaclass, which a native image cannot do for a JDK method nothing registered'
            !calls.any { it.startsWith('INVOKEDYNAMIC') }
    }
}
