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
package grails.web.mapping

import spock.lang.Issue
/**
 * @author Dmitrii Galinskii
 */
class MandatoryParamMappingSpec extends AbstractUrlMappingsSpec {

  @Issue('GRAILS-11863')
  void "Test mandatory param"() {
    given:"A link generator with a dynamic URL mapping"
    def linkGenerator = getLinkGenerator {
      "/community/sort/$sort" (controller: 'community', action: 'list')
      "/community" (controller: 'community', action: 'list')
      "/index/$sort?" (controller: 'index', action: 'index')
      "/index/$sort/list" (controller: 'index', action: 'list')
    }

    expect:
    linkGenerator.link(controller:"community", action: 'list', params:[sort:'test']) == 'http://localhost/community/sort/test'
    linkGenerator.link(controller:"community", action: 'list') == 'http://localhost/community'
    linkGenerator.link(controller:"index", action: 'index') == 'http://localhost/index'
    linkGenerator.link(controller:"index", action: 'index', params:[sort:'test']) == 'http://localhost/index/test'
    linkGenerator.link(controller:"index", action: 'list', params:[sort:'test']) == 'http://localhost/index/test/list'
    linkGenerator.link(controller:"index", action: 'list') == 'http://localhost/index/list'
  }
}
