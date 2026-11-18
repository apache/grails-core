<%--
  ~  Licensed to the Apache Software Foundation (ASF) under one
  ~  or more contributor license agreements.  See the NOTICE file
  ~  distributed with this work for additional information
  ~  regarding copyright ownership.  The ASF licenses this file
  ~  to you under the Apache License, Version 2.0 (the
  ~  "License"); you may not use this file except in compliance
  ~  with the License.  You may obtain a copy of the License at
  ~
  ~    https://www.apache.org/licenses/LICENSE-2.0
  ~
  ~  Unless required by applicable law or agreed to in writing,
  ~  software distributed under the License is distributed on an
  ~  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  ~  KIND, either express or implied.  See the License for the
  ~  specific language governing permissions and limitations
  ~  under the License.
  --%>
<html>
<head>
	<meta name="layout" content="${layoutUi}"/>
	<s2ui:title messageCode='spring.security.ui.aclSid.search'/>
</head>
<body>
<s2ui:formContainer type='search' beanType='aclSid' width='40rem'>
	<s2ui:searchForm>
		<div class="mb-3">
			<label class="form-label" for="sid"><g:message code='aclSid.sid.label' default='SID'/></label>
			<g:textField name='sid' class='form-control' maxlength='255' autocomplete='off' value='${sid}'/>
		</div>
		<div class="mb-3">
			<span class="form-label d-block"><g:message code='aclSid.principal.label' default='Principal'/></span>
			<g:radioGroup name='principal'
			              labels='${[message(code: "spring.security.ui.search.true"), message(code: "spring.security.ui.search.false"), message(code: "spring.security.ui.search.either")]}'
			              values='[1,-1,0]' value='${principal ?: 0}'>
				<div class="form-check form-check-inline">
					<%= it.radio.toString().replace('<input ', '<input class="form-check-input" ') %>
					<span class="form-check-label">${it.label}</span>
				</div>
			</g:radioGroup>
		</div>
	</s2ui:searchForm>
</s2ui:formContainer>
<g:if test='${searched}'>
<div class="table-responsive">
	<table class="table table-striped table-hover align-middle">
		<thead>
		<tr>
			<s2ui:sortableColumn property='sid' titleDefault='SID'/>
			<s2ui:sortableColumn property='principal' titleDefault='Principal'/>
		</tr>
		</thead>
		<tbody>
		<g:each in='${results}' var='aclSid'>
			<tr>
				<td><g:link action='edit' id='${aclSid.id}'>${uiPropertiesStrategy.getProperty(aclSid, 'sid')}</g:link></td>
				<td><s2ui:formatBoolean bean='${aclSid}' name='principal'/></td>
			</tr>
		</g:each>
		</tbody>
	</table>
</div>
<s2ui:paginate total='${totalCount}'/>
</g:if>
<s2ui:ajaxSearch paramName='sid'/>
</body>
</html>
