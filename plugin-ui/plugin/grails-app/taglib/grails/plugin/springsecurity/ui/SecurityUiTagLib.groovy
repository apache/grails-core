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

		String focus = attrs.remove('focus') != 'false' ? '.focus()' : ''

		writeDocumentReady out, """\
	\$("#$paramName")${focus}.autocomplete({
		minLength: $minLength,
		cache: false,
		source: "${createLink('ajaxSearch', null, [paramName: paramName])}"
	});"""
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

		out << """
			<tr class="prop">
				<td valign="top" class="name">
					<label for="$name">${message(code: labelCode(name), default: labelCodeDefault)}</label>
				</td>
				<td valign="top" class="value ${hasErrors(bean: bean, field: name, 'errors')}">
					${checkBox([name: name, value: uiPropertiesStrategy.getProperty(bean, name)] + attrs)}
					${fieldErrors(bean, name)}
				</td>
			</tr>"""
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

		def value = formatDate(date: uiPropertiesStrategy.getProperty(bean, name),
		                       formatName: 'spring.security.ui.dateFormatGsp')

		out << """
			<tr class="prop">
				<td valign="top" class="name">
					<label for="$name">${message(code: labelCode(name), default: labelCodeDefault)}</label>
				</td>
				<td valign="top" class="value ${hasErrors(bean: bean, field: name, 'errors')}">
					${textField([name: name, value: value, maxlength: '20'] + attrs)}
					${fieldErrors(bean, name)}
				</td>
			</tr>"""

		writeDocumentReady out, "\t\$('#$name').datepicker({ dateFormat: '${message(code:'spring.security.ui.dateFormatJs')}' });"
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
		out << """<a id="deleteButton">${message(code:'spring.security.ui.button.delete.label')}</a>"""
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
			<div id="deleteConfirmDialog" title="${message(code:'default.button.delete.confirm.message')}"></div>"""

		writeDocumentReady out, """
	\$("#deleteButton").button().bind('click', function() {
		\$('#deleteConfirmDialog').dialog('open');
	});

	\$("#deleteConfirmDialog").dialog({
		autoOpen: false, resizable: false, height: 100, modal: true,
		buttons: [
			{ text: "${message(code:'spring.security.ui.button.delete.label')}", id: '#confirmDelete',
			  click: function() { document.forms.deleteForm.submit(); } },
			{ text: "${message(code:'spring.security.ui.button.cancel.label')}",
			  click: function() { \$(this).dialog('close'); } }
		]
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
				${hiddenField(name: idName ?: 'id', value: id)}
				${hiddenField(name: 'version', value: bean?.version)}"""
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
					${hiddenField(name: SynchronizerTokensHolder.TOKEN_KEY, value: tokensHolder.generateToken(request.forwardURI))}
					${hiddenField(name: SynchronizerTokensHolder.TOKEN_URI, value: request.forwardURI)}"""
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
	 * @attr height   the height, to override the CSS default
	 * @attr width    the width (defaults to '100%'
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
		}
		else if (type == 'forgotPassword' || type == 'register' || type == 'resetPassword' || type == 'securityQuestions') {
			title = message(code: 'spring.security.ui.' + type + '.header')
		}
		else {
			title = message(code: 'default.' + (type == 'save' ? 'create' : 'edit') + '.label', args: [pageScope.entityName])
		}

		Integer height = attrs.remove('height') as Integer
		def width = attrs.remove('width') ?: '100%'

		String heightStyle = height ? " height: ${height}px;" : ''

		out << """
			<div class="ui-widget-content s2ui_form s2ui_center" id="formContainer" style="width: $width;$heightStyle">
				<div class="ui-dialog-titlebar ui-widget-header ui-corner-all ui-helper-clearfix s2ui_center" style='padding: 10px;'>
					<span style="-moz-user-select: none;" unselectable="on" class="ui-dialog-title">$title</span>
				</div>
				${body()}
			</div>"""

		writeDocumentReady out, "\t\$('#formContainer').resizable();"

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
		out << """<a href="${createLink(attrs).encodeAsHTML()}" id="$elementId" """
// TODO encodeAsHTML

		writeRemainingAttributes out, attrs
		out << '>' << text << '</a>'

		writeDocumentReady out, """\t\$("#$elementId").button();"""
	}

	/**
	 * @attr controller   REQUIRED the controller name
	 * @attr itemAction   if present render just a menu item
	 * @attr searchOnly   if true omit the item to create (defaults to false)
	 * @attr submenu      if true renders nested (defaults to false)
	 */
	def menu = { attrs ->
		attrs = [:] + attrs

		String controller = getRequiredAttribute(attrs, 'controller', 'submenu')
		String itemAction = attrs.remove('itemAction')
		String indent = '\t\t\t\t\t'

		String messageKey = 'spring.security.ui.menu.' + controller
		if (itemAction) {
			messageKey += '.' + itemAction
		}
		String caption = message(code: messageKey)

		if (itemAction) {
			out << '<li><a href="' << createLink(itemAction, controller) << '">' << caption << '</a></li>'
			return
		}

		boolean searchOnly = attrs.remove('searchOnly')
		boolean showList = attrs.remove('showList')
		boolean noSearch = attrs.remove('noSearch')
		boolean submenu = attrs.remove('submenu')

		def lines = []
		if (submenu) {
			lines << "<li>$caption &raquo;"
			indent += '\t\t'
		}
		else {
			lines << """<li><a class="accessible">$caption</a>"""
		}
		lines << indent + '\t<ul>'
		if(!noSearch) {
			lines << """$indent\t\t<li><a href="${createLink('search', controller)}">${message(code: 'spring.security.ui.search')}</a></li>"""
		}
		if(showList) {
			lines << """$indent\t\t<li><a href="${createLink('index', controller)}">${message(code: 'spring.security.ui.list')}</a></li>"""
		}
		if (!searchOnly) {
			lines << """$indent\t\t<li><a href="${createLink('create', controller)}">${message(code:'spring.security.ui.create')}</a></li>"""
		}
		lines << indent + '\t</ul>'
		lines << indent + '</li>'

		lines.each { out << it << '\n' }
	}

	/**
	 * @attr total REQUIRED the total number of results
	 */
	def paginate = { attrs ->
		attrs = [:] + attrs

		String summary
		int total = getRequiredAttribute(attrs, 'total', 'paginationSummary') as int
		if (total == 0) {
			summary = message(code:'spring.security.ui.search.noResults')
		}
		else {
			int max = params.int('max')
			int offset = params.int('offset')

			int from = offset + 1
			int to = offset + max
			if (to > total) {
				to = total
			}
			summary = message(code:'spring.security.ui.search.summary', args: [from, to, total])

			out << """
				<div class="paginateButtons">
					${g.paginate(total: total, params: pageScope.queryParams)}
				</div>"""
		}

		out << """\n\t\t\t\t\t\t<div style="text-align:center;">$summary</div>"""
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

		out << """
			<tr class="prop">
				<td valign="top" class="name">
					<label for="$id">${message(code: labelCode(name), default: labelCodeDefault)}</label>
				</td>
				<td valign="top" class="value ${hasErrors(bean: bean, field: name, 'errors')}">
					${passwordField([name: name, id: id, value: uiPropertiesStrategy.getProperty(bean, name)] + attrs)}
					${fieldErrors(bean, name)}
				</td>
			</tr>"""
	}

	/**
	 */
	def required = { attrs ->
		// TODO use this
		out << "<span class='s2ui_required'>*&nbsp;</span>"
	}

	/**
	 * @attr colspan REQUIRED number of td elements per tr row
	 */
	def searchForm = { attrs, body ->
		attrs = [:] + attrs

		int colspan = getRequiredAttribute(attrs, 'colspan', 'searchForm') as int

		pageScope.s2uiFormName = 'search'

		out << """
			<form action="${createLink('search')}" method="post" name="search" id="search">
				<br/>
				<table>
					<tbody>
					${body()}
					<tr><td colspan="$colspan">&nbsp;</td></tr>
					<tr>
						<td colspan="$colspan">
							${s2ui:submitButton(elementId:'searchButton', messageCode:'spring.security.ui.search')}
						</td>
					</tr>
					</tbody>
				</table>
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
			<table class='info'>
				<caption>${message(code: 'spring.security.ui.menu.securityInfo.' + type, args: captionArgs)}</caption>"""

		if (headerCodes) {
			out << """
				<thead>
					<tr>"""

			for (code in headerCodes.split(',')) {
				out << """
						<th>${message(code: 'spring.security.ui.info.' + type + '.header.' + code)}</th>"""
			}

			out << """
					</tr>
				</thead>"""
		}

		out << """
				<tbody>"""

		if (items) {
			items.eachWithIndex { item, int i ->
				out << """
					<tr class="${(i % 2) == 0 ? 'even' : 'odd'}">"""

				out << body(item)

				out << """
					</tr>"""
			}
		}
		else {
			out << body()
		}

		out << """
				</tbody>
			</table>"""
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

		out << """
			<tr class="prop">
				<td valign="top" class="name">
					<label for="$name">${message(code: labelCode(fieldName), default: labelCodeDefault)}</label>
				</td>
				<td valign="top" class="value ${hasErrors(bean: bean, field: fieldName, 'errors')}">
					${select(selectAttrs)} ${fieldError(bean: bean, field: fieldName)}
				</td>
			</tr>"""
	}

	/**
	 */
	def showFlash = { attrs ->
		String message = flash.remove('message')
		String error = flash.remove('error')
		if (!message && !error) {
			return
		}

		String clazz = message ? 'icon icon_info' : 'icon icon_error'
		String text = (message ?: error).encodeAsHTML()

		writeDocumentReady out, """\t\$.jGrowl('<span class="$clazz">$text</span>', { life: 10000 });"""
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
			 titleCode = titleCode[0..indexDot-1]
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
			}
			else if (formName == 'updateForm') {
				elementId = 'update'
			}
			else {
				throwTagError("Tag [$namespace:submitButton] is missing required attribute [elementId]")
			}
			if (!text) {
				if (formName == 'saveForm') {
					text = message(code: 'default.button.create.label')
				}
				else if (formName == 'updateForm') {
					text = message(code: 'default.button.update.label')
				}
				else {
					throwTagError("Tag [$namespace:submitButton] is missing required attribute [messageCode or text]")
				}
			}
		}

		def out = getOut()
		out << """<a id="$elementId" """
		writeRemainingAttributes out, attrs
		out << ">$text</a><input type='submit' value=' ' id='${elementId}_submit' class='s2ui_hidden_button'>"

		writeDocumentReady out, """\
	\$("#$elementId").button();
	\$('#$elementId').bind('click', function() {
		document.forms.${formName}.submit();
	});"""
	}

	/**
	 * @attr name   REQUIRED the name
	 * @attr height REQUIRED the height
	 */
	def tab = { attrs, body ->
		attrs = [:] + attrs

		String name = getRequiredAttribute(attrs, 'name', 'tab')
		def height = getRequiredAttribute(attrs, 'height', 'tab')

		out << """
			<div id="tab-$name">
				<div class="s2ui_section" style="height: $height; overflow: auto;">
				${body()}
				</div>
			</div>"""
	}

	/**
	 * @attr data      REQUIRED a list of maps with data to build the tabs
	 * @attr elementId REQUIRED the HTML id
	 * @attr height    REQUIRED minimum height (int)
	 */
	def tabs = { attrs, body ->
		attrs = [:] + attrs

		def id = getRequiredAttribute(attrs, 'elementId', 'tabs')
		def data = getRequiredAttribute(attrs, 'data', 'tabs')
		int height = getRequiredAttribute(attrs, 'height', 'tabs') as int

		def out = getOut()
		out << """<div style='display: none;' id="$id">\n"""
		out << "\t\t\t\t\t\t<ul>\n"
		for (element in data) {
			out << """\t\t\t\t\t\t\t<li><a href="#tab-$element.name" class="icon $element.icon">$element.message</a></li>\n"""
		}
		out << "\t\t\t\t\t\t</ul>\n"

		out << body()
		out << "</div>\n"

		writeDocumentReady out, """\t\$("#$id").tabs().show().resizable({minHeight: $height, minWidth: 100});"""
	}

	def cmdValidationFields= { attrs ->
		attrs = [:] + attrs
		def outtxt = ''
		def validations = attrs.remove('myfields')
		def validationItems = attrs.remove('validations') ?: []
		def user = attrs.remove('user')
		def validationUserLookUpProperty = attrs.remove('validationUserLookUpProperty')
		def instance = grailsApplication.getClassForName(attrs.remove('domainClassName'))
		if(instance instanceof Object) {
            def myInstance = instance.findWhere((validationUserLookUpProperty): user)
            if (myInstance instanceof Object) {
		        validations?.eachWithIndex{ it,idx ->
                	String label = ''
					String name = it.prop
					if (it.labelDomain) {
						label = uiPropertiesStrategy.getProperty(myInstance, it.labelDomain)
					} else {
						label = message(code: it.labelMessage, defaul: it.domain + " " + it.prop)
					}

					outtxt += this.textFieldRow([value: validationItems[idx]?.valueTxt, errorMsg:validationItems[idx]?.errorMsg,size:25,labelCodeDefault: label, name: name, useBean: false])
				}
				outtxt = '<table>' + outtxt + '</table>'
			} else {
				outtxt = '<div>' + message(code: 'spring.security.ui.securityQuestion.MissingQuestions') + '</div>'
			}
		}
		out <<  outtxt
	}




	/**
	 * @attr name             REQUIRED the HTML element name
	 * @attr labelCodeDefault the default to display if there's no message for the i18n code
	 */
	def textFieldRow = { attrs ->
		attrs = [:] + attrs
		def useBean = attrs.remove('useBean')
		if((useBean && useBean.size() > 0 && useBean instanceof String) || useBean instanceof Boolean ) {
			useBean = Boolean.valueOf(useBean)
		} else {
			useBean = true
		}
		String labelCodeDefault = attrs.remove('labelCodeDefault')
		String name
		def value
		def bean = beanFromModel()
		def errors = ""
		if(useBean) {
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

		out << """
			<tr class="prop">
				<td valign="top" class="name">
					<label for="$name">${message(code: labelCode(name), default: labelCodeDefault)}</label>
				</td>
				<td valign="top" class="value ${ useBean ? hasErrors(bean: bean, field: fieldName, 'errors') : errors.size() > 0 ? 'error' : ''}">
					${textField(textFieldAttrs)}
					${useBean ? fieldErrors(bean, fieldName) : "<span class='s2ui_error'>${errors}</span>"}
				</td>
			</tr>"""
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
			set(var: 'entityName', value: entityName)
		}

		out << "<title>${message(code: messageCode, args: args)}</title>"
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
		writer << asset.script { """
\$(function() {
$javascript
});\n"""
		}
	}

	protected void writeRemainingAttributes(writer, attrs) {
		writer << attrs.collect { k, v -> """ $k="$v" """ }.join('')
	}

	protected String createLink(String action, String controller = controllerName, Map params = null) {
		g.createLink(action: action, controller: controller, params: params)
	}

	protected String fieldErrors(bean, field) {
		if (!bean) {
			return
		}

		def attrs = [bean: bean, field: field]
		def sb = new StringBuilder()
		eachError attrs, { sb << "<span class='s2ui_error'>${message(error: it, encodeAs: 'HTML')}</span>" }
		sb
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
