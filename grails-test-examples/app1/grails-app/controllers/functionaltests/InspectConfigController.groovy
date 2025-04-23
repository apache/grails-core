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

package functionaltests
class InspectConfigController {

    def showPropertyValues() {
        def env = applicationContext.environment
        def cfg = grailsApplication.config.grails11951
        def prop1 = cfg.prop1
        def prop2 = cfg.prop2
        def prop3 = cfg.prop3
        def prop1Flat = grailsApplication.config.getProperty('grails11951.prop1')
        def prop2Flat = grailsApplication.config.getProperty('grails11951.prop2')
        def prop3Flat = grailsApplication.config.getProperty('grails11951.prop3')
        [env:env,
          prop1: prop1,
          prop2: prop2,
          prop3: prop3,
          prop1Flat: prop1Flat,
          prop2Flat: prop2Flat,
          prop3Flat: prop3Flat]
    }
}
