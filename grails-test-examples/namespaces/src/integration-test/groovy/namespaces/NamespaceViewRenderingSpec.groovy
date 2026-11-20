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

    void "Test namespace is inferred for links rendered from an admin namespaced page"() {
        when:"An admin namespaced page renders URL-generating tags"
        go('/myAppTest/admin/page/links')

        then:"Links without an explicit namespace target admin controllers"
        $('#bookLink').@href.contains('/myAppTest/admin/book/index')
        $('#bookCreateLink').@href.contains('/myAppTest/admin/book/list')
        $('#reportLink').@href.contains('/myAppTest/admin/report/index')
        $('#currentPageLink').@href.contains('/myAppTest/admin/page/index')
        $('#customBookLink').@href.contains('/myAppTest/admin/book/index')

        and:"Form-related tags infer the admin namespace"
        $('#bookForm').@action.contains('/myAppTest/admin/book/save')
        $('#bookFormActionSubmit').@formaction.contains('/myAppTest/admin/book/alternateSave')

        and:"Pagination, sortable column, and include infer the admin namespace"
        $('#bookPagination a.step').first().@href.contains('/myAppTest/admin/book/list')
        $('#pageSortable a').@href.contains('/myAppTest/admin/page/list')
        $('#bookInclude').text().contains('Admin Book Include')

        and:"Explicit namespace and explicit null namespace override inference"
        $('#frontendPageLink').@href.contains('/myAppTest/frontend/page/index')
        $('#rootReportLink').@href.contains('/myAppTest/report/index')
        !$('#rootReportLink').@href.contains('/myAppTest/admin/report')
    }

    void "Test namespace is inferred for redirects and chains from an admin namespaced controller"() {
        when:"An admin action redirects to book without an explicit namespace"
        go('/myAppTest/admin/page/redirectToBook')

        then:"The redirect lands on the admin book controller"
        waitFor { currentUrl.contains('/myAppTest/admin/book/index') }

        when:"An admin action chains to book without an explicit namespace"
        go('/myAppTest/admin/page/chainToBook')

        then:"The chain lands on the admin book controller"
        waitFor { currentUrl.contains('/myAppTest/admin/book/index') }

        when:"An admin action opts out with an explicit null namespace"
        go('/myAppTest/admin/page/redirectToRootReport')

        then:"The redirect lands on the root report controller"
        waitFor { currentUrl.contains('/myAppTest/report/index') }
        !currentUrl.contains('/myAppTest/admin/report')
    }
}
