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
package grails.test.mixin

import grails.testing.web.GrailsWebUnitTest
import spock.lang.Issue
import spock.lang.Specification

class GroovyPageUnitTestMixinWithCustomViewDirSpec extends Specification implements GrailsWebUnitTest {

    Closure doWithConfig() {{ c ->
        def customViewDir = new File('.', 'src/test/resources/customviews')
        c['grails.gsp.view.dir'] = customViewDir.absolutePath
    }}
    
    @Issue('GRAILS=11543')
    void 'test rendering a template when grails.gsp.view.dir has been assigned a value'() {
        when:
        def result = render(template: '/demo/myTemplate')
        
        then:
        result.trim() == 'this is a custom template'
    }
}
