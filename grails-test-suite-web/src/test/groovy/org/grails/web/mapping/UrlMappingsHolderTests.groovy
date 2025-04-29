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
import org.springframework.core.io.*
import spock.lang.Specification

class UrlMappingsHolderTests extends Specification implements GrailsWebUnitTest {

    def mappingScript = '''
mappings {
  "/blog/$entry/$year?/$month?/$day?" {
        controller = "blog"
        action = "show"
  }
  "/book/$author/$title/$test" {
      controller = "book"
      action = { "${params.test}" }
  }
  "/$controller/$action?/$id?" {
      controller = { params.controller }
      action = { params.action }
      id = { params.id }
  }
}
'''

   def mappingScript2 = '''
mappings {
    "/specific/$action?" {
        controller = "someController"
    }

    "/admin/$controller/$action?/$id?" {
        constraints {
            id(matches:/\\d+/)
        }
    }
}
   '''

   def mappingWithNamedArgs = '''
mappings {
    "/author/$lastName/$firstName" (controller:'product', action:'show')
}
'''
   def mappingWithNamedArgsAndClosure = '''
mappings {
    "/author/$lastName/$firstName" (controller:'product') {
         action = 'show'
    }
}
'''

    def mappingWithNamespace = '''
mappings {
    "/$namespace/$controller/$action?/$id?" ()
}
'''

    void testGetReverseMappingWithNamespace() {
        given:
        def res = new ByteArrayResource(mappingWithNamespace.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        def holder = new DefaultUrlMappingsHolder(mappings)

        when:
        def m = holder.getReverseMapping("product", "show", "foo", null, [:])

        then:
        "/foo/product/show" == m.createURL("product", "show", "foo", null, [:], "utf-8")
    }

    void testGetReverseMappingWithNamedArgsAndClosure() {
        when:
        def res = new ByteArrayResource(mappingWithNamedArgsAndClosure.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        def holder = new DefaultUrlMappingsHolder(mappings)

        def params = [lastName:'Winter', firstName:'Johnny']
        def m = holder.getReverseMapping("product", "show", params)

        then:
        "/author/Winter/Johnny" == m.createURL(params, "utf-8")
    }

    void testGetReverseMappingWithNamedArgs() {
        given:
        def res = new ByteArrayResource(mappingWithNamedArgs.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        def holder = new DefaultUrlMappingsHolder(mappings)

        when:
        def params = [lastName:'Winter', firstName:'Johnny']
        def m = holder.getReverseMapping("product", "show", params)

        then:
        "/author/Winter/Johnny" == m.createURL(params, "utf-8")
    }

    void testGetReverseMappingWithExcessArgs() {
        given:
        def res = new ByteArrayResource(mappingScript.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        def holder = new DefaultUrlMappingsHolder(mappings)

        when:
        // test with exact argument match
        Map vars = [entry: "foo", year: 2007, month: 3, day: 17, some: "other"]
        def m = holder.getReverseMapping("blog", "show", vars)

        then:
        "/blog/foo/2007/3/17?some=other" == m.createRelativeURL("blog", "show", vars, "utf-8")
    }

    void testGetReverseMappingWithVariables() {
        given:
        def res = new ByteArrayResource(mappingScript2.bytes)
        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        def holder = new DefaultUrlMappingsHolder(mappings)

        when:
        // test with fewer arguments
        def m = holder.getReverseMapping("test", "list",null)

        then:
        "/admin/test/list/1" == m.createURL(controller:"test", action:"list",id:1, "utf-8")

        // make sure caching works as expected
        "/admin/test/list/1" == m.createURL("test", "list", [id:1], "utf-8")
        "/admin/test/list/1" == m.createURL("test", "list", [id:1], "utf-8")

        "/admin/test/list/1?foo=bar" == m.createURL(controller:"test", action:"list",id:1, foo:"bar", "utf-8")

        when:
        m = holder.getReverseMapping("someController", "test", null)

        then:
        "/specific/test" == m.createURL(controller:"someController", action:"test", "utf-8")
    }

    void testGetReverseMappingWithFewerArgs() {
        given:
        def res = new ByteArrayResource(mappingScript.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        // use un-cached holder for testing
        def holder = new DefaultUrlMappingsHolder(mappings,null,true)
        holder.setUrlCreatorMaxWeightedCacheCapacity(0)
        holder.initialize()

        when:
        // test with fewer arguments
        def m = holder.getReverseMapping("blog", "show", [entry:"foo", year:2007])

        then:
        "blog" == m.controllerName
        "show" == m.actionName
    }

    void testGetReverseMappingWithExactArgs() {
        given:
        def res = new ByteArrayResource(mappingScript.bytes)

        def evaluator = new DefaultUrlMappingEvaluator(applicationContext)
        def mappings = evaluator.evaluateMappings(res)

        // use un-cached holder for testing
        def holder = new DefaultUrlMappingsHolder(mappings,null,true)
        holder.setUrlCreatorMaxWeightedCacheCapacity(0)
        holder.initialize()

        when:
        // test with exact argument match
        def m = holder.getReverseMapping("blog", "show", [entry:"foo", year:2007, month:3, day:17])

        then:
        "blog" == m.controllerName
        "show" == m.actionName
        "/blog/foo/2007/3/17?test=test" == m.createURL([controller:"blog",action:"show",entry:"foo",year:2007,month:3,day:17,test:'test'], "utf-8")

        when:
        // test with fewer arguments
        m = holder.getReverseMapping("blog", "show", [entry:"foo", year:2007])

        then:
        "blog" == m.controllerName
        "show" == m.actionName

        when:
        m = holder.getReverseMapping("book", null, [author:"dierk", title:"GINA", test:3])

        then:
        "book" == m.controllerName

        when:
        m = holder.getReverseMapping(null, null, [:])

        then:
        m
    }
}
