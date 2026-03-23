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

import grails.util.GrailsWebMockUtil
import grails.web.mapping.AbstractUrlMappingsSpec
import org.springframework.web.context.request.RequestContextHolder

class GroupDefaultsSpec extends AbstractUrlMappingsSpec {

    void "group defaults are inherited by child mappings"() {
        given:
        def holder = getUrlMappingsHolder {
            group "/api", namespace: 'api', controller: 'resource', {
                "/list"(action: 'list')
                "/show"(action: 'show')
            }
        }

        when:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def infos = holder.matchAll('/api/list')

        then:
        infos.length == 1
        infos[0].controllerName == 'resource'
        infos[0].namespace == 'api'
        infos[0].actionName == 'list'

        when:
        webRequest.resetParams()
        infos = holder.matchAll('/api/show')

        then:
        infos.length == 1
        infos[0].controllerName == 'resource'
        infos[0].namespace == 'api'
        infos[0].actionName == 'show'
    }

    void "child mappings can override group defaults"() {
        given:
        def holder = getUrlMappingsHolder {
            group "/api", namespace: 'api', controller: 'defaultCtrl', {
                "/special"(controller: 'specialCtrl', action: 'handle')
                "/normal"(action: 'index')
            }
        }

        when:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def infos = holder.matchAll('/api/special')

        then: "the child override takes effect"
        infos.length == 1
        infos[0].controllerName == 'specialCtrl'
        infos[0].namespace == 'api'
        infos[0].actionName == 'handle'

        when: "the child inherits the default controller"
        webRequest.resetParams()
        infos = holder.matchAll('/api/normal')

        then:
        infos.length == 1
        infos[0].controllerName == 'defaultCtrl'
        infos[0].namespace == 'api'
        infos[0].actionName == 'index'
    }

    void "group without defaults still works"() {
        given:
        def holder = getUrlMappingsHolder {
            group "/legacy", {
                "/foo"(controller: 'foo', action: 'bar')
            }
        }

        when:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def infos = holder.matchAll('/legacy/foo')

        then:
        infos.length == 1
        infos[0].controllerName == 'foo'
        infos[0].actionName == 'bar'
    }

    void "nested groups inherit outer group defaults"() {
        given:
        def holder = getUrlMappingsHolder {
            group "/community", namespace: 'community', {
                group "/topics", controller: 'topic', {
                    "/gallery"(action: 'gallery')
                    "/create"(action: 'create')
                }
            }
        }

        when:
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def infos = holder.matchAll('/community/topics/gallery')

        then:
        infos.length == 1
        infos[0].controllerName == 'topic'
        infos[0].namespace == 'community'
        infos[0].actionName == 'gallery'
    }

    void cleanup() {
        RequestContextHolder.resetRequestAttributes()
    }
}
