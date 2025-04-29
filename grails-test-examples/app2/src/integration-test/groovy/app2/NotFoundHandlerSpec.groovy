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

package app2

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
@Rollback
class NotFoundHandlerSpec extends ContainerGebSpec {

    void "Test that a 404 handler renders the view correctly when a forward is executed"() {
        when:"An action is visited where an interceptor uses response.sendError(404)"
            go '/foo/index'

        then:"The 404 handler is rendered"
        	title == "Page Not Found"

        when:"No response.sendError(404) method is called"
            go '/foo/index?user=admin'

        then:"The 404 handler is not executed"
            title == "Foo List"            
    }
}
