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

package grails.artefact.controller.support

import jakarta.servlet.http.HttpServletRequest
import spock.lang.Specification

class AllowedMethodsHelperSpec extends Specification {
    
    void 'test isAllowed method'() {
        expect:
        expectedValue == AllowedMethodsHelper.isAllowed(actionName, [getMethod: {requestMethod}] as HttpServletRequest, allowedMethods)
        
        where:
        expectedValue | actionName | requestMethod | allowedMethods
        
        false         | 'alpha'    | 'DELETE'      | [alpha: 'POST']
        false         | 'alpha'    | 'DELETE'      | [alpha: ['POST', 'PUT']]
        false         | 'alpha'    | 'dElEtE'      | [alpha: 'pOsT']
        false         | 'alpha'    | 'DeLeTe'      | [alpha: ['pOsT', 'pUT']]
        
        true          | 'alpha'    | 'DELETE'      | [alpha: 'DELETE']
        true          | 'alpha'    | 'DELETE'      | [beta: 'POST']
        true          | 'alpha'    | 'DELETE'      | [alpha: ['POST', 'DELETE']]
        true          | 'alpha'    | 'DElEtE'      | [alpha: 'dElEtE']
        true          | 'alpha'    | 'DeLeTe'      | [alpha: ['pOsT', 'dElEtE']]
    }
}
