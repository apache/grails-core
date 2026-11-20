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
package grails.plugin.springsecurity

/**
 * Tests the computed {@code allowedMethods} property that lets tooling (such as the
 * create-app welcome page) discover the POST-only restriction on {@link LogoutController}.
 */
class LogoutControllerSpec extends AbstractUnitSpec {

    void 'allowedMethods restricts index to POST while logout.postOnly is active by default'() {
        expect:
        LogoutController.allowedMethods == [index: 'POST']
    }

    void 'allowedMethods declares no restriction when logout.postOnly is disabled'() {
        when:
        ReflectionUtils.setConfigProperty 'logout.postOnly', false

        then:
        LogoutController.allowedMethods == [:]
    }
}
