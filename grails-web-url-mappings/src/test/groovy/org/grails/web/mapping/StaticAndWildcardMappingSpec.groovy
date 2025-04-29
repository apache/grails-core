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

import grails.web.mapping.AbstractUrlMappingsSpec
import spock.lang.Issue

/**
 * @author Dmitrii Galinskii
 */

class StaticAndWildcardMappingSpec extends AbstractUrlMappingsSpec {

  @Issue('GRAILS-11864')
  void "Test the correct link is generated for mapping with static and and Wildcard parts in one token"() {
    given:"A link generator with a dynamic URL mapping"
    def linkGenerator = getLinkGenerator {
      "/list/type/${type}_filter"(controller: "index", action: "index")
      "/list/type/$type"(controller: "index", action: "index")
      "/list/type1"(controller: "index", action: "index")
    }

    expect:
    linkGenerator.link(controller:"index", action: 'index', params:[type:'test']) == 'http://localhost/list/type/test_filter'
    linkGenerator.link(controller:"index", action: 'index') == 'http://localhost/list/type1'
  }
}
