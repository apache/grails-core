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
package grails.plugin.springsecurity.ui

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.ui.strategy.PropertiesStrategy
import org.grails.web.servlet.mvc.SynchronizerTokensHolder

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class SecurityUiTagLib {

    static namespace = 's2ui'

    /** Dependency injection for the 'uiPropertiesStrategy' bean. */
    PropertiesStrategy uiPropertiesStrategy

    /**
     * @attr paramName REQUIRED the property to search on
     * @attr focus     if true, set the focus to the field at document ready (defaults to true)
     * @attr minLength the minimum number of chars to type before search starts
     */
    def ajaxSearch = { attrs ->
        attrs = [:] + attrs

        String paramName = getRequiredAttribute(attrs, 'paramName', 'ajaxSearch')
        int minLength = (attrs.remove('minLength') as Integer) ?: 3

        if (attrs.remove('focus') != 'false') {
            writeDocumentReady out, "\t\$('#$paramName').focus();"
        }

        String url = createLink('ajaxSearch', null, [paramName: paramName])

        writeDocumentReady out, """\
    (function() {
        var input = document.getElementById('$paramName');
        if (!input) return;
        var list = document.createElement('datalist');
        list.id = '${paramName}_datalist';
        input.setAttribute('list', list.id);
        input.parentNode.appendChild(list);
        input.addEventListener('input', function() {
            var term = input.value;
            if (term.length < $minLength) return;
            fetch('$url&term=' + encodeURIComponent(term), { headers: { Accept: 'application/json' } })
                .then(function(response) { return response.json(); })
                .then(function(items) {
                    list.replaceChildren();
                    (items || []).forEach(function(item) {
                        var option = document.createElement('option');
                        option.value = (item && item.value) || item;
                        list.appendChild(option);
                    });
                });
        });
    })();"""
    }

    /**
     * @attr name             REQUIRED the HTML element name
     * @attr labelCodeDefault the default to display if there's no message for the i18n code
     */
    def checkboxRow = { attrs ->
        attrs = [:] + attrs

        def bean = beanFromModel()
        String name = getRequiredAttribute(attrs, 'name', 'checkboxRow')
        String labelCodeDefault = attrs.remove('labelCodeDefault')

        String invalid = fieldHasErrors(bean, name) ? ' is-invalid' : ''

        out << """
            <div class="mb-3 form-check">
                ${g.checkBox([name: name, value: uiPropertiesStrategy.getProperty(bean, name), class: 'form-check-input' + invalid] + attrs)}
                <label class="form-check-label" for="$name">${message(code: labelCode(name), default: labelCodeDefault)}</label>
                ${fieldErrors(bean, name)}
            </div>"""
    }

    /**
     * @attr name             REQUIRED the HTML element name
     * @attr labelCodeDefault the default to display if there's no message for the i18n code
     */
    def dateFieldRow = { attrs ->
        attrs = [:] + attrs

        def bean = beanFromModel()
        String labelCodeDefault = attrs.remove('labelCodeDefault')
        String name = getRequiredAttribute(attrs, 'name', 'dateFieldRow')

        def value = g.formatDate(date: uiPropertiesStrategy.getProperty(bean, name),
                formatName: 'spring.security.ui.dateFormatGsp')

        String invalid = fieldHasErrors(bean, name) ? ' is-invalid' : ''

        out << """
            <div class="mb-3">
                <label class="form-label" for="$name">${message(code: labelCode(name), default: labelCodeDefault)}</label>
                ${g.textField([name: name, value: value, maxlength: '20', class: 'form-control' + invalid] + attrs)}
                ${fieldErrors(bean, name)}
            </div>"""
    }

    /**
     * @attr src REQUIRED the src
     */
    def deferredScript = { attrs ->
        attrs = [:] + attrs

        String src = getRequiredAttribute(attrs, 'src', 'deferredScript')

        def deferred = request.s2uiDeferredScripts
        if (!deferred) {
            deferred = request.s2uiDeferredScripts = []
        }
        deferred << src
    }

    /**
     */
    def deferredScripts = { attrs ->
        request.s2uiDeferredScripts.each { out << asset.javascript(src: it) << '\n' }
        out << asset.deferredScripts().replaceAll('</script><script >', '')
    }

    /**
     */
    def deleteButton = { attrs ->
        out << """<button type="button" id="deleteButton" class="btn btn-danger" data-bs-toggle="modal" data-bs-target="#deleteConfirmModal">${message(code: 'spring.security.ui.button.delete.label')}</button>"""
    }

    /**
     * @attr instanceId REQUIRED the id of the domain class instance
     * @attr useToken OPTIONAL
     */
    def deleteButtonForm = { attrs ->
        attrs = [:] + attrs
        boolean useToken = extractFormUseToken(attrs)

        def id = getRequiredAttribute(attrs, 'instanceId', 'deleteButton')

        attrs.action = 'delete'

        out << """
            <form action="${createLink(attrs)}" method="post" name="deleteForm" id="deleteForm">"""

        writeTokenFields(useToken)

        out << """
                <input type="hidden" name="id" value="$id" />
            </form>
            <div class="modal fade" id="deleteConfirmModal" tabindex="-1" aria-labelledby="deleteConfirmLabel" aria-hidden="true">
                <div class="modal-dialog modal-dialog-centered">
                    <div class="modal-content">
                        <div class="modal-header">
                            <h1 class="modal-title fs-5" id="deleteConfirmLabel">${message(code: 'default.button.delete.confirm.message')}</h1>
                            <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="${message(code: 'spring.security.ui.button.cancel.label')}"></button>
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">${message(code: 'spring.security.ui.button.cancel.label')}</button>
                            <button type="button" class="btn btn-danger" id="confirmDelete">${message(code: 'spring.security.ui.button.delete.label')}</button>
                        </div>
                    </div>
                </div>
            </div>"""

        writeDocumentReady out, """
    \$('#confirmDelete').on('click', function() {
        document.forms.deleteForm.submit();
    });"""
    }

    /**
     * Uses the asset:script tag to create a block of deferred code that will be rendered at the end
     * of the body and will be wrapped in a jQuery function that fires when the document is ready.
     */
    def documentReady = { attrs, body ->
        writeDocumentReady out, body()
    }

    /**
     * @attr beanName the model name of the domain class or command object instance when not a child
     *                of formContainer or if nonstandard (e.g. command object)
     * @attr class    the CSS class
     * @attr focus    the element to focus on document ready
     * @attr idName   the name of the id property if it's not 'id'
     * @attr type     'save', 'update', 'login' if provided, only when not a child of formContainer
     * @attr useToken 'true' or 'false'.  Set to 'true' to prevent CSRF attacks
     */
    def form = { attrs, body ->
        attrs = [:] + attrs

        boolean useToken = extractFormUseToken(attrs)
        String type = extractFormType(attrs)
        def bean = extractFormBean(attrs)
        String cssClass = extractFormCssClass(attrs)
        String idName = attrs.remove('idName')
        def id = extractFormId(bean, idName)
        String name = pageScope.s2uiFormName = type + 'Form'
        String autocomplete = (type == 'login' || type.endsWith('Password')) ? ' autocomplete="off"' : ''
        String link = generateFormLink(type)

        out << """
            <form action="$link"$cssClass method="post" name="$name" id="$name"$autocomplete>"""

        writeTokenFields(useToken)

        if (type == 'update') {
            out << """
                ${g.hiddenField(name: idName ?: 'id', value: id)}
                ${g.hiddenField(name: 'version', value: bean?.version)}"""
        }

        out << """
                ${body()}
            </form>"""

        String focus = attrs.remove('focus')
        if (focus) {
            writeDocumentReady out, "\t\$('#$focus').focus();"
        }

        pageScope.s2uiBean = null
        pageScope.s2uiBeanType = null
        pageScope.s2uiFormName = null
        pageScope.s2uiFormType = null
    }

    protected void writeTokenFields(boolean useToken) {
        if (useToken) {
            def tokensHolder = SynchronizerTokensHolder.store(session)
            out << """
                    ${g.hiddenField(name: SynchronizerTokensHolder.TOKEN_KEY, value: tokensHolder.generateToken(request.forwardURI))}
                    ${g.hiddenField(name: SynchronizerTokensHolder.TOKEN_URI, value: request.forwardURI)}"""
        }
    }

    protected boolean extractFormUseToken(Map attrs) {
        boolean useToken = false
        if (attrs.containsKey('useToken')) {
            useToken = attrs['useToken'].asBoolean()
            attrs.remove('useToken')
        }
        return useToken
    }

    protected String generateFormLink(String type) {
        String link
        if (type == 'login') {
            link = "$request.contextPath$SpringSecurityUtils.securityConfig.apf.filterProcessesUrl"
        } else {
            link = createLink(type)
        }
        return link
    }

    protected Object extractFormId(bean, String idName) {
        def id
        if (bean && !(bean instanceof CommandObject)) {
            id = idName ? bean[idName] : bean.id
        }
        return id
    }

    protected String extractFormCssClass(Map attrs) {
        String cssClass = attrs.remove('class') ?: ''
        if (cssClass) {
            cssClass = """class="$cssClass" """
        }
        return cssClass
    }

    protected Object extractFormBean(Map attrs) {
        def bean
        String beanName = attrs.remove('beanName')
        if (beanName) {
            pageScope.s2uiBeanType = beanName
            bean = pageScope.s2uiBean = pageScope[beanName]
            assert bean
        } else {
            bean = pageScope.s2uiBean
        }
        return bean
    }

    protected String extractFormType(Map attrs) {
        String type = attrs.remove('type')
        if (type) {
            pageScope.s2uiFormType = type
        } else {
            type = pageScope.s2uiFormType
        }
        assert type
        return type
    }

    /**
     * @attr type     REQUIRED one of 'save', 'update', 'search', 'register', 'forgotPassword', or 'resetPassword'
     * @attr beanType when type is 'search' this is the bean type, e.g. aclEntry, registrationCode, etc. and when
     *                it's 'save' or 'update' it's the model name for the bean
     * @attr focus    the element to focus on document ready
     * @attr height   ignored (retained for backwards compatibility)
     * @attr width    the max width (defaults to '100%')
     */
    def formContainer = { attrs, body ->
        attrs = [:] + attrs

        String type = pageScope.s2uiFormType = getRequiredAttribute(attrs, 'type', 'formContainer')
        def bean
        String beanType = attrs.remove('beanType')
        if (beanType) {
            pageScope.s2uiBeanType = beanType
            if (type != 'search') {
                bean = pageScope.s2uiBean = pageScope[beanType]
                assert bean
            }
        }

        String title
        if (type == 'search') {
            title = message(code: 'spring.security.ui.' + beanType + '.search')
        } else if (type == 'forgotPassword' || type == 'register' || type == 'resetPassword' || type == 'securityQuestions') {
            title = message(code: 'spring.security.ui.' + type + '.header')
        } else {
            title = message(code: 'default.' + (type == 'save' ? 'create' : 'edit') + '.label', args: [pageScope.entityName])
        }

        attrs.remove('height')
        def width = attrs.remove('width') ?: '100%'

        out << """
            <div class="card shadow-sm mx-auto mb-4" id="formContainer" style="max-width: $width;">
                <div class="card-header">
                    <h1 class="h5 mb-0">$title</h1>
                </div>
                <div class="card-body">
                ${body()}
                </div>
            </div>"""

        String focus = attrs.remove('focus')
        if (focus) {
            writeDocumentReady out, "\t\$('#$focus').focus();"
        }

        pageScope.s2uiBean = null
        pageScope.s2uiBeanType = null
        pageScope.s2uiFormType = null
    }

    /**
     * @attr bean REQUIRED the domain class instance
     * @attr name REQUIRED the 'params' name to lookup the real property name or the real property name
     */
    def formatBoolean = { attrs ->
        attrs = [:] + attrs

        out << g.formatBoolean(boolean: uiPropertiesStrategy.getProperty(
                getRequiredAttribute(attrs, 'bean', 'formatBoolean'),
                getRequiredAttribute(attrs, 'name', 'formatBoolean')))
    }

    /**
     * @attr elementId   REQUIRED the HTML id
     * @attr messageCode the i18n code for the text
     * @attr text        the button value if there's no messageCode attr
     */
    def linkButton = { attrs ->
        attrs = [:] + attrs

        String text = resolveText(attrs)
        String elementId = getRequiredAttribute(attrs, 'elementId', 'linkButton')

        def out = getOut()
        out << """<a href="${createLink(attrs).encodeAsHTML()}" id="${elementId.encodeAsHTML()}" class="btn btn-outline-secondary" """
        writeRemainingAttributes out, attrs
        out << '>' << (text?.encodeAsHTML() ?: '') << '</a>'
    }

    /**
     * @attr controller   REQUIRED the controller name
     * @attr itemAction   if present render just a menu item
     * @attr searchOnly   if true omit the item to create (defaults to false)
     * @attr submenu      if true renders items for a parent dropdown with a header (defaults to false)
     */
    def menu = { attrs ->
        attrs = [:] + attrs

        String controller = getRequiredAttribute(attrs, 'controller', 'submenu')
        String itemAction = attrs.remove('itemAction')

        String messageKey = 'spring.security.ui.menu.' + controller
        if (itemAction) {
            messageKey += '.' + itemAction
        }
        String caption = message(code: messageKey)

        if (itemAction) {
            out << """<li><a class="dropdown-item" href="${createLink(itemAction, controller)}">$caption</a></li>"""
            return
        }

        boolean searchOnly = attrs.remove('searchOnly')
        boolean showList = attrs.remove('showList')
        boolean noSearch = attrs.remove('noSearch')
        boolean submenu = attrs.remove('submenu')

        def items = []
        if (!noSearch) {
            items << """<li><a class="dropdown-item" href="${createLink('search', controller)}">${message(code: 'spring.security.ui.search')}</a></li>"""
        }
        if (showList) {
            items << """<li><a class="dropdown-item" href="${createLink('index', controller)}">${message(code: 'spring.security.ui.list')}</a></li>"""
        }
        if (!searchOnly) {
            items << """<li><a class="dropdown-item" href="${createLink('create', controller)}">${message(code: 'spring.security.ui.create')}</a></li>"""
        }

        if (submenu) {
            // items for a parent dropdown, grouped under a header
            out << """<li><h6 class="dropdown-header">$caption</h6></li>\n"""
            items.each { out << it << '\n' }
        } else {
            out << """<li class="nav-item dropdown">
                <a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false">$caption</a>
                <ul class="dropdown-menu">\n"""
            items.each { out << it << '\n' }
            out << '</ul>\n</li>\n'
        }
    }

    /**
     * @attr total REQUIRED the total number of results
     */
    def paginate = { attrs ->
        attrs = [:] + attrs

        String summary
        int total = getRequiredAttribute(attrs, 'total', 'paginationSummary') as int
        if (total == 0) {
            summary = message(code: 'spring.security.ui.search.noResults')
        } else {
            int max = params.int('max')
            int offset = params.int('offset')

            int from = offset + 1
            int to = offset + max
            if (to > total) {
                to = total
            }
            summary = message(code: 'spring.security.ui.search.summary', args: [from, to, total])

            out << """
                <nav class="d-flex justify-content-center gap-2 my-2" aria-label="${message(code: 'spring.security.ui.search')}">
                    ${g.paginate(total: total, params: pageScope.queryParams)}
                </nav>"""
        }

        out << """\n\t\t\t\t\t\t<p id="searchSummary" class="text-center text-body-secondary small">$summary</p>"""
    }

    /**
     * @attr name             REQUIRED the HTML element name
     * @attr id               the DOM id
     * @attr labelCodeDefault the default to display if there's no message for the i18n code
     */
    def passwordFieldRow = { attrs ->
        attrs = [:] + attrs

        def bean = beanFromModel()
        String labelCodeDefault = attrs.remove('labelCodeDefault')
        String name = getRequiredAttribute(attrs, 'name', 'passwordFieldRow')

        String id = attrs.remove('id') ?: name

        String invalid = fieldHasErrors(bean, name) ? ' is-invalid' : ''

        out << """
            <div class="mb-3">
                <label class="form-label" for="$id">${message(code: labelCode(name), default: labelCodeDefault)}</label>
                ${g.passwordField([name: name, id: id, value: uiPropertiesStrategy.getProperty(bean, name), class: 'form-control' + invalid] + attrs)}
                ${fieldErrors(bean, name)}
            </div>"""
    }

    /**
     */
    def required = { attrs ->
        // TODO use this
        out << "<span class='text-danger'>*&nbsp;</span>"
    }

    /**
     * @attr colspan ignored (retained for backwards compatibility)
     */
    def searchForm = { attrs, body ->
        attrs = [:] + attrs

        attrs.remove('colspan')

        pageScope.s2uiFormName = 'search'

        out << """
            <form action="${createLink('search')}" method="post" name="search" id="search">
                ${body()}
                <button type="submit" id="searchButton" class="btn btn-primary">${message(code: 'spring.security.ui.search')}</button>
            </form>"""

        pageScope.s2uiFormName = null
    }

    /**
     * @attr type        REQUIRED the type
     * @attr captionArgs optional args for the caption i18n code
     * @attr headerCodes the th tag i18n codes, a comma-delimited string
     * @attr items       optional items to loop over each in a tr
     */
    def securityInfoTable = { attrs, body ->
        attrs = [:] + attrs

        String type = getRequiredAttribute(attrs, 'type', 'securityInfoTable')
        String headerCodes = attrs.remove('headerCodes') ?: ''
        def items = attrs.remove('items')
        def captionArgs = attrs.remove('captionArgs')

        out << """
            <table class="table table-striped table-hover align-middle">
                <caption class="caption-top">${message(code: 'spring.security.ui.menu.securityInfo.' + type, args: captionArgs)}</caption>"""

        if (headerCodes) {
            out << '''
                <thead>
                    <tr>'''

            for (code in headerCodes.split(',')) {
                out << """
                        <th scope="col">${message(code: 'spring.security.ui.info.' + type + '.header.' + code)}</th>"""
            }

            out << '''
                    </tr>
                </thead>'''
        }

        out << '''
                <tbody>'''

        if (items) {
            items.eachWithIndex { item, int i ->
                out << '''
                    <tr>'''

                out << body(item)

                out << '''
                    </tr>'''
            }
        } else {
            out << body()
        }

        out << '''
                </tbody>
            </table>'''
    }

    /**
     * @attr from             REQUIRED the items used to populate the select
     * @attr name             REQUIRED the HTML element name
     * @attr labelCodeDefault the default to display if there's no message for the i18n code
     * @attr noSelection      displayed when nothing is selected
     * @attr optionKey        the property name to lookup from the items in 'from'
     * @attr optionValue      the option name
     */
    def selectRow = { attrs ->
        attrs = [:] + attrs

        def bean = beanFromModel()
        def from = getRequiredAttribute(attrs, 'from', 'selectRow')
        String labelCodeDefault = attrs.remove('labelCodeDefault')
        String name = getRequiredAttribute(attrs, 'name', 'selectRow')
        def noSelection = attrs.remove('noSelection')
        def optionKey = attrs.remove('optionKey')
        def optionValue = attrs.remove('optionValue')

        def value = uiPropertiesStrategy.getProperty(bean, name)

        def selectAttrs = [name: name, from: from, value: value, noSelection: noSelection,
                           optionKey: optionKey ?: 'id', optionValue: optionValue]

        String fieldName = name
        if (name.endsWith('.id')) {
            fieldName = selectAttrs.id = name[0..-4]
            selectAttrs.value = value?.id
        }

        String invalid = fieldHasErrors(bean, fieldName) ? ' is-invalid' : ''
        selectAttrs.class = 'form-select' + invalid

        out << """
            <div class="mb-3">
                <label class="form-label" for="$name">${message(code: labelCode(fieldName), default: labelCodeDefault)}</label>
                ${g.select(selectAttrs)}
                ${fieldErrors(bean, fieldName)}
            </div>"""
    }

    /**
     * Renders any flash message/error as a Bootstrap alert. Prefer the core
     * <g:flashMessages/> tag; this remains for backwards compatibility and
     * delegates to it.
     */
    def showFlash = { attrs ->
        out << g.flashMessages([:])
    }

    /**
     * @attr property     REQUIRED the property name
     * @attr titleDefault REQUIRED the i18n default
     */
    def sortableColumn = { attrs ->
        attrs = [:] + attrs

        String property = getRequiredAttribute(attrs, 'property', 'sortableColumn')

        String titleCode = property
        int indexDot = titleCode.lastIndexOf('.')
        if (indexDot != -1) {
            titleCode = titleCode[0..indexDot - 1]
        }
        titleCode = controllerName + '.' + titleCode + '.label'
        String titleDefault = getRequiredAttribute(attrs, 'titleDefault', 'sortableColumn')

        out << g.sortableColumn(property: property, params: pageScope.queryParams,
                title: message(code: titleCode, default: titleDefault))
    }

    /**
     * @attr src REQUIRED the src
     */
    def stylesheet = { attrs ->
        attrs = [:] + attrs

        String src = getRequiredAttribute(attrs, 'src', 'stylesheet')

        def sb = new StringBuilder('\n')
        asset.stylesheet(src: src).eachLine { if (it.trim()) sb << '\t\t' << it.trim() << '\n' }
        out << sb
    }

    /**
     * @attr elementId   the HTML id (optional if the formName is 'saveForm')
     * @attr messageCode the i18n code for the text (optional if the formName is 'saveForm' or 'updateForm')
     * @attr text        the button text if there's no messageCode attr
     */
    def submitButton = { attrs ->
        attrs = [:] + attrs

        String elementId = attrs.remove('elementId')
        String text = resolveText(attrs)
        String formName = pageScope.s2uiFormName

        if (!elementId) {
            if (formName == 'saveForm') {
                elementId = 'create'
            } else if (formName == 'updateForm') {
                elementId = 'update'
            } else {
                throwTagError("Tag [$namespace:submitButton] is missing required attribute [elementId]")
            }
            if (!text) {
                if (formName == 'saveForm') {
                    text = message(code: 'default.button.create.label')
                } else if (formName == 'updateForm') {
                    text = message(code: 'default.button.update.label')
                } else {
                    throwTagError("Tag [$namespace:submitButton] is missing required attribute [messageCode or text]")
                }
            }
        }

        def out = getOut()
        out << """<button type="submit" id="${elementId.encodeAsHTML()}" class="btn btn-primary" """
        writeRemainingAttributes out, attrs
        out << ">${text?.encodeAsHTML() ?: ''}</button>"
    }

    /**
     * @attr name   REQUIRED the name
     * @attr height ignored (retained for backwards compatibility)
     */
    def tab = { attrs, body ->
        attrs = [:] + attrs

        String name = getRequiredAttribute(attrs, 'name', 'tab')
        attrs.remove('height')

        String active = ''
        if (pageScope._s2uiTabFirst == null || pageScope._s2uiTabFirst) {
            active = ' show active'
            pageScope._s2uiTabFirst = false
        }

        out << """
            <div class="tab-pane fade$active" id="tab-$name" role="tabpanel">
                ${body()}
            </div>"""
    }

    /**
     * @attr data      REQUIRED a list of maps with data to build the tabs
     * @attr elementId REQUIRED the HTML id
     * @attr height    ignored (retained for backwards compatibility)
     */
    def tabs = { attrs, body ->
        attrs = [:] + attrs

        def id = getRequiredAttribute(attrs, 'elementId', 'tabs')
        def data = getRequiredAttribute(attrs, 'data', 'tabs')
        attrs.remove('height')

        def out = getOut()
        out << """<ul class="nav nav-tabs" id="$id" role="tablist">\n"""
        data.eachWithIndex { element, int i ->
            String active = i == 0 ? ' active' : ''
            String selected = i == 0 ? 'true' : 'false'
            out << """\t<li class="nav-item" role="presentation"><button class="nav-link$active" id="tab-$element.name-tab" data-bs-toggle="tab" data-bs-target="#tab-$element.name" type="button" role="tab" aria-controls="tab-$element.name" aria-selected="$selected">$element.message</button></li>\n"""
        }
        out << '</ul>\n'

        pageScope._s2uiTabFirst = true
        out << '<div class="tab-content pt-3">\n'
        out << body()
        out << '</div>\n'
        pageScope._s2uiTabFirst = null
    }

    def cmdValidationFields = { attrs ->
        attrs = [:] + attrs
        def outtxt = ''
        def validations = attrs.remove('myfields')
        def validationItems = attrs.remove('validations') ?: []
        def user = attrs.remove('user')
        def validationUserLookUpProperty = attrs.remove('validationUserLookUpProperty')
        def instance = grailsApplication.getClassForName(attrs.remove('domainClassName'))
        if (instance instanceof Object) {
            def myInstance = instance.findWhere((validationUserLookUpProperty): user)
            if (myInstance instanceof Object) {
                validations?.eachWithIndex { it, idx ->
                    String label = ''
                    String name = it.prop
                    if (it.labelDomain) {
                        label = uiPropertiesStrategy.getProperty(myInstance, it.labelDomain)
                    } else {
                        label = message(code: it.labelMessage, defaul: it.domain + ' ' + it.prop)
                    }

                    outtxt += this.textFieldRow([value: validationItems[idx]?.valueTxt, errorMsg: validationItems[idx]?.errorMsg, size: 25, labelCodeDefault: label, name: name, useBean: false])
                }
            } else {
                outtxt = '<div>' + message(code: 'spring.security.ui.securityQuestion.MissingQuestions') + '</div>'
            }
        }
        out << outtxt
    }

    /**
     * @attr name             REQUIRED the HTML element name
     * @attr labelCodeDefault the default to display if there's no message for the i18n code
     */
    def textFieldRow = { attrs ->
        attrs = [:] + attrs
        def useBean = attrs.remove('useBean')
        if ((useBean && useBean.size() > 0 && useBean instanceof String) || useBean instanceof Boolean) {
            useBean = Boolean.valueOf(useBean)
        } else {
            useBean = true
        }
        String labelCodeDefault = attrs.remove('labelCodeDefault')
        String name
        def value
        def bean = beanFromModel()
        def errors = ''
        if (useBean) {
            name = getRequiredAttribute(attrs, 'name', 'textFieldRow')
            value = uiPropertiesStrategy.getProperty(bean, name)
        } else {
            name = attrs.remove('name')
            value = attrs.remove('value') ?: ''
            errors = attrs.remove('errorMsg') ?: ''
        }
        def textFieldAttrs = [name: name, value: value] + attrs

        String fieldName = name
        if (name.endsWith('.id')) {
            fieldName = textFieldAttrs.id = name[0..-4]
            textFieldAttrs.value = value?.id
        }

        String invalid = useBean ? (fieldHasErrors(bean, fieldName) ? ' is-invalid' : '') : (errors ? ' is-invalid' : '')
        textFieldAttrs.class = 'form-control' + invalid

        out << """
            <div class="mb-3">
                <label class="form-label" for="$name">${message(code: labelCode(name), default: labelCodeDefault)}</label>
                ${g.textField(textFieldAttrs)}
                ${useBean ? fieldErrors(bean, fieldName) : "<div class='invalid-feedback d-block'>${errors}</div>"}
            </div>"""
    }

    /**
     * @attr messageCode           REQUIRED the i18n message code
     * @attr entityNameMessageCode the i18n message code for the 'entityName'
     * @attr entityNameDefault     the i18n default value for the 'entityName'
     */
    def title = { attrs ->
        attrs = [:] + attrs

        String messageCode = getRequiredAttribute(attrs, 'messageCode', 'title')
        String entityNameMessageCode = attrs.remove('entityNameMessageCode')
        String entityNameDefault = attrs.remove('entityNameDefault')

        def args
        if (entityNameMessageCode) {
            String entityName = message(code: entityNameMessageCode, default: entityNameDefault)
            args = [entityName]
            pageScope.entityName = entityName
        }

        // A literal <title> in GSP source is rewritten by the compile-time
        // preprocessor into grailsLayout:wrapTitleTag/captureTitle so layouts
        // can read it through g:layoutTitle; a taglib-emitted <title> bypasses
        // that capture, so invoke the same tags directly.
        String titleText = message(code: messageCode, args: args)
        out << grailsLayout.wrapTitleTag([:], { ->
            grailsLayout.captureTitle([:], { -> titleText })
        })
    }

    protected getRequiredAttribute(attrs, String name, String tagName) {
        if (!attrs.containsKey(name)) {
            throwTagError("Tag [$namespace:$tagName] is missing required attribute [$name]")
        }
        attrs.remove name
    }

    protected String resolveText(attrs) {
        String messageCode = attrs.remove('messageCode')
        messageCode ? message(code: messageCode) : attrs.remove('text')
    }

    protected void writeDocumentReady(writer, javascript) {
        // DOMContentLoaded fires after the host layout's classic end-of-body
        // scripts (jQuery included), so plugin pages rendered through a host
        // layout can rely on jQuery inside the callback regardless of where
        // this inline script lands in the document.
        writer << asset.script {
            """
document.addEventListener('DOMContentLoaded', function() {
$javascript
});\n"""
        }
    }

    protected void writeRemainingAttributes(writer, attrs) {
        writer << attrs.collect { k, v -> """ ${k.toString().encodeAsHTML()}="${v.toString().encodeAsHTML()}" """ }.join('')
    }

    protected String createLink(String action, String controller = controllerName, Map params = null) {
        g.createLink(action: action, controller: controller, params: params)
    }

    protected boolean fieldHasErrors(bean, String field) {
        if (bean == null || !bean.hasProperty('errors')) {
            return false
        }
        bean.errors?.hasFieldErrors(field)
    }

    protected String fieldErrors(bean, field) {
        if (!bean) {
            return ''
        }

        def attrs = [bean: bean, field: field]
        def sb = new StringBuilder()
        g.eachError(attrs, { sb << "<div class='invalid-feedback d-block'>${message(error: it, encodeAs: 'HTML')}</div>" })
        sb.toString()
    }

    protected beanFromModel() {
        def bean = pageScope.s2uiBean
        assert bean
        bean
    }

    protected String labelCode(String propertyName) {
        String beanType = pageScope.s2uiBeanType
        assert beanType
        beanType + '.' + propertyName + '.label'
    }
}
