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
package org.grails.web.mapping

import grails.testing.web.GrailsWebUnitTest
import spock.lang.Specification
import org.springframework.core.io.*

class UrlMappingEvaluatorTests extends Specification implements GrailsWebUnitTest {

    def mappingScript = '''
mappings {
  "/$id/$year?/$month?/$day?" {
        controller = "blog"
        action = "show"
        constraints {
            year(matches:/\\d{4}/)
            month(matches:/\\d{2}/)
        }
  }

  "/product/$name" {
        controller = "product"
        action = "show"
  }
  "/book/$author/$title/$test" {
      controller = "book"
  }

  "/author/$lastName/$firstName" (controller:'author', action:'show') {
     constraints {
        lastName(maxSize:5)
        firstName(maxSize:5)
     }
  }

  "/music/$band/$album" (controller:'music', action:'show')

  "/myFiles/something-$fname.$fext" {
      controller = "files"
  }

  "/long/$path**"(controller: 'files')
}
'''

    void testEvaluateMappings() {
        given:
        def res = new ByteArrayResource(mappingScript.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        expect:
        mappings.size() == 7

        when:
        def m = mappings[0]

        then:
        "/(*)/(*)?/(*)?/(*)?" == m.urlData.urlPattern
        4 == m.urlData.tokens.size()

        when:
        def info = m.match("/myentry/2007/04/28")

        then:
        "myentry" == info.id
        "2007" == info.parameters.year
        "04" == info.parameters.month
        "28" == info.parameters.day
        "blog" == info.controllerName
        "show" == info.actionName

        m.match("/myentry/2007/04/28")
        m.match("/myentry/2007/04")
        m.match("/myentry/2007")
        m.match("/myentry")

        when:
        m = mappings[1]
        info = m.match("/product/MacBook")

        then:
        "MacBook" == info.parameters.name

        !m.match("/product")
        !m.match("/foo/bar")
        !m.match("/product/MacBook/foo")

        when:
        m = mappings[3]
        info = m.match("/author/Brown/Jeff")

        then:
        info
        "Brown" == info.parameters.lastName
        "Jeff" == info.parameters.firstName
        "show" == info.actionName
        "author" == info.controllerName

        // first name too long
        !m.match("/author/Lang/Johnny")

        // both names too long
        !m.match("/author/Winter/Johnny")

        // last name too long
        !m.match("/author/Winter/Edgar")

        when:
        info = mappings[4].match("/music/Rush/Hemispheres")

        then:
        info
        "Rush" == info.parameters.band
        "Hemispheres" == info.parameters.album
        "show" == info.actionName
        "music" == info.controllerName

        when:
        info = mappings[5].match("/myFiles/something-hello.txt")

        then:
        info
        "files" == info.controllerName
        "hello" == info.parameters.fname
        "txt" == info.parameters.fext

        when:
        // Test the double-wildcard, "**".
        info = mappings[6].match("/long/path/to/some/file")

        then:
        info
        "path/to/some/file" == info.parameters.path
        "files" == info.controllerName
    }
}
