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

package namespaces

import grails.gorm.transactions.Rollback
import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration

/**
 */
@Integration(applicationClass = Application)
@Rollback
class NamespaceViewRenderingSpec extends ContainerGebSpec {

    void "Test view rendering works as expected when namespaces are used"() {
        when:"When an implicit namespace is used"
        go('/myAppTest/test/implicitView')

        then:"The view is rendered"
        	$('body').text() == "Implicit View Rendered!"

        when:"When an explicit view with a namespace is used"
        go('/myAppTest/test/explicitView')

        then:"The view is rendered"
        	$('body').text() == "Foo View Rendered"
    }
}
