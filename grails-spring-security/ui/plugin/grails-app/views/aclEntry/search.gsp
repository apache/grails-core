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
	<s2ui:title messageCode='spring.security.ui.aclEntry.search'/>
</head>
<body>
<s2ui:formContainer type='search' beanType='aclEntry' focus='aclObjectIdentity' width='40rem'>
	<s2ui:searchForm>
		<div class="mb-3">
			<label class="form-label" for="aclObjectIdentity"><g:message code='aclEntry.aclObjectIdentity.label' default='AclObjectIdentity'/></label>
			<g:textField name='aclObjectIdentity.id' id='aclObjectIdentity' class='form-control' maxlength='255' value='${aclObjectIdentity}'/>
		</div>
		<div class="mb-3">
			<label class="form-label" for="aceOrder"><g:message code='aclEntry.aceOrder.label' default='Ace Order'/></label>
			<g:textField name='aceOrder' class='form-control' maxlength='255' value='${aceOrder}'/>
		</div>
		<div class="mb-3">
			<label class="form-label" for="sid"><g:message code='aclEntry.sid.label' default='SID'/></label>
			<g:select name='sid.id' id='sid' class='form-select' from='${sids}' optionKey='id' optionValue='sid' value='${sid}' noSelection="['null': 'All']"/>
		</div>
		<div class="mb-3">
			<label class="form-label" for="mask"><g:message code='aclEntry.mask.label' default='Mask'/></label>
			<g:textField name='mask' class='form-control' maxlength='255' value='${mask}'/>
		</div>
		<div class="mb-3">
			<span class="form-label d-block"><g:message code='aclEntry.granting.label' default='Granting'/></span>
			<g:radioGroup name='granting'
			              labels='${[message(code: "spring.security.ui.search.true"), message(code: "spring.security.ui.search.false"), message(code: "spring.security.ui.search.either")]}'
			              values='[1,-1,0]' value='${granting ?: 0}'>
				<div class="form-check form-check-inline">
					<%= it.radio.toString().replace('<input ', '<input class="form-check-input" ') %>
					<span class="form-check-label">${it.label}</span>
				</div>
			</g:radioGroup>
		</div>
		<div class="mb-3">
			<span class="form-label d-block"><g:message code='aclEntry.auditSuccess.label' default='Audit Success'/></span>
			<g:radioGroup name='auditSuccess'
			              labels='${[message(code: "spring.security.ui.search.true"), message(code: "spring.security.ui.search.false"), message(code: "spring.security.ui.search.either")]}'
			              values='[1,-1,0]' value='${auditSuccess ?: 0}'>
				<div class="form-check form-check-inline">
					<%= it.radio.toString().replace('<input ', '<input class="form-check-input" ') %>
					<span class="form-check-label">${it.label}</span>
				</div>
			</g:radioGroup>
		</div>
		<div class="mb-3">
			<span class="form-label d-block"><g:message code='aclEntry.auditFailure.label' default='Audit Failure'/></span>
			<g:radioGroup name='auditFailure'
			              labels='${[message(code: "spring.security.ui.search.true"), message(code: "spring.security.ui.search.false"), message(code: "spring.security.ui.search.either")]}'
			              values='[1,-1,0]' value='${auditFailure ?: 0}'>
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
			<s2ui:sortableColumn property='id' titleDefault='ID'/>
			<s2ui:sortableColumn property='aclObjectIdentity.id' titleDefault='AclObjectIdentity'/>
			<s2ui:sortableColumn property='aceOrder' titleDefault='Ace Order'/>
			<s2ui:sortableColumn property='sid.id' titleDefault='SID'/>
			<s2ui:sortableColumn property='mask' titleDefault='Mask'/>
			<s2ui:sortableColumn property='granting' titleDefault='Granting'/>
			<s2ui:sortableColumn property='auditSuccess' titleDefault='Audit Success'/>
			<s2ui:sortableColumn property='auditFailure' titleDefault='Audit Failure'/>
		</tr>
		</thead>
		<tbody>
		<g:each in='${results}' var='entry'>
			<tr>
				<g:set var='entryAclObjectIdentity' value='${uiPropertiesStrategy.getProperty(entry, "aclObjectIdentity")}'/>
				<g:set var='entrySid' value='${uiPropertiesStrategy.getProperty(entry, "sid")}'/>
				<td><g:link action='edit' id='${entry.id}'>${entry.id}</g:link></td>
				<td><g:link action='edit' controller='aclObjectIdentity' id='${entryAclObjectIdentity.id}'>${entryAclObjectIdentity.id}</g:link></td>
				<td>${entry.aceOrder}</td>
				<td><g:link action='edit' controller='aclSid' id='${entrySid.id}'>${uiPropertiesStrategy.getProperty(entrySid, 'sid')}</g:link></td>
				<td>${permissionFactory.buildFromMask(entry.mask)}</td>
				<td><s2ui:formatBoolean bean='${entry}' name='granting'/></td>
				<td><s2ui:formatBoolean bean='${entry}' name='auditSuccess'/></td>
				<td><s2ui:formatBoolean bean='${entry}' name='auditFailure'/></td>
			</tr>
		</g:each>
		</tbody>
	</table>
</div>
<s2ui:paginate total='${totalCount}'/>
</g:if>
</body>
</html>
