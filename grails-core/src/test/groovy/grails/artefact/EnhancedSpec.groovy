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
package grails.artefact

import spock.lang.Specification

class EnhancedSpec extends Specification {

    void 'enhancedFor and mixins default to an empty array while version is mandatory'() {
        given:
        Enhanced enhanced = WithDefaults.getAnnotation(Enhanced)

        expect:
        enhanced.version() == '1.0'
        enhanced.enhancedFor().length == 0
        enhanced.mixins().length == 0
    }

    void 'enhancedFor and mixins can be explicitly declared'() {
        given:
        Enhanced enhanced = WithExplicitValues.getAnnotation(Enhanced)

        expect:
        enhanced.version() == '2.0'
        enhanced.enhancedFor() == ['Controller', 'Service'] as String[]
        enhanced.mixins() == [String, Number] as Class[]
    }

    @Enhanced(version = '1.0')
    static class WithDefaults {

    }

    @Enhanced(version = '2.0', enhancedFor = ['Controller', 'Service'], mixins = [String, Number])
    static class WithExplicitValues {

    }

}
