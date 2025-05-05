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

import spock.lang.Specification
import spock.lang.Unroll

class TestFrameworkSpec extends Specification {

    @Unroll("expected test source path: #expected for path: #path , lang: #lang and test framework: #testFramework")
    void "getSourcePath returns a path with the correct language extension and test framework suffix"(Language lang,
                                                                                                      TestFramework testFramework,
                                                                                                      String expected,
                                                                                                      String path) {
        expect:
        expected == testFramework.getSourcePath(path, lang)

        where:
        lang            | testFramework       || expected
        Language.GROOVY | TestFramework.JUNIT || "src/test/groovy/{packagePath}/{className}Test.groovy"
        Language.GROOVY | TestFramework.SPOCK || "src/test/groovy/{packagePath}/{className}Spec.groovy"
        path = '/{packagePath}/{className}'
    }

    @Unroll("getDefaultLanguage for test framework: #testFramework return #expected")
    void "verify the default language for a test framework"(Language expected, TestFramework testFramework) {
        expect:
        expected == testFramework.getDefaultLanguage()

        where:
        expected        | testFramework
        Language.GROOVY | TestFramework.SPOCK
    }

    @Unroll("getSupportedLanguages for test framework: #testFramework return #expected")
    void "verify the list of supported languages for a test framework"(List<Language> expected, TestFramework testFramework) {
        given:
        expect:
        expected.sort { a, b -> a.name <=> b.name } ==
                testFramework.supportedLanguages.sort { a, b -> a.name <=> b.name }

        where:
        expected          | testFramework
        [Language.GROOVY] | TestFramework.JUNIT
        [Language.GROOVY] | TestFramework.SPOCK
    }
}
