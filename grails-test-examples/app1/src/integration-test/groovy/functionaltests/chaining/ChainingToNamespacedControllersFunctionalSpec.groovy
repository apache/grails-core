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

package functionaltests.chaining

import functionaltests.Application
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

@Integration(applicationClass = Application)
class ChainingToNamespacedControllersFunctionalSpec extends ContainerGebSpec {

    void "Test chaining to a namespaced controller"() {
        when:"A controller action chains to a namespaced controller"
            go '/chainingHome/chainDemo?ns=alpha'

        then:"The chain works correctly"
        	$('body').text() == 'rendered from the Demo controller in the alpha namespace'

    when:"A controller action chains to a namespaced controller"
        go '/chainingHome/chainDemo?ns=beta'

    then:"The chain works correctly"
        $('body').text() == 'rendered from the Demo controller in the beta namespace'
    }
}
