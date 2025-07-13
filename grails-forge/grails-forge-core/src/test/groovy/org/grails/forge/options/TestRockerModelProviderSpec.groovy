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

package org.grails.forge.options

import com.fizzed.rocker.RockerModel
import spock.lang.Specification
import spock.lang.Unroll

class TestRockerModelProviderSpec extends Specification {

    @Unroll
    void "a delegate method is defined for language: #language and test framework: #testFramework "(Language language,
                                                                                                    TestFramework testFramework) {
        given:
        TestRockerModelProvider provider = new TestRockerModelProvider() {

            @Override
            RockerModel spock() {
                return null
            }

            @Override
            RockerModel groovyJunit() {
                return null
            }
        }

        when:
        provider.findModel(language, testFramework)

        then:
        noExceptionThrown()

        where:
        [language, testFramework] << [Language.values(), TestFramework.values()].combinations()
    }
}

