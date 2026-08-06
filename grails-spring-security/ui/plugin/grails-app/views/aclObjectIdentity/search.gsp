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
	<s2ui:title messageCode='spring.security.ui.aclObjectIdentity.search'/>
</head>
<body>
<s2ui:formContainer type='search' beanType='aclObjectIdentity' focus='objectId' width='40rem'>
	<s2ui:searchForm>
		<div class="mb-3">
			<label class="form-label" for="aclClass"><g:message code='aclObjectIdentity.aclClass.label' default='AclClass'/></label>
			<g:select name='aclClass.id' id='aclClass' class='form-select' from='${classes}' optionKey='id' optionValue='className' value='${aclClass}' noSelection="['null': 'All']"/>
		</div>
		<div class="mb-3">
			<label class="form-label" for="objectId"><g:message code='aclObjectIdentity.objectId.label' default='Object ID'/></label>
			<g:textField name='objectId' class='form-control' maxlength='255' value='${objectId}'/>
		</div>
		<div class="mb-3">
			<label class="form-label" for="owner"><g:message code='aclObjectIdentity.owner.label' default='Owner'/></label>
			<g:select name='owner.id' id='owner' class='form-select' from='${sids}' optionKey='id' optionValue='sid' value='${pageScope.owner}' noSelection="['null': 'All']"/>
		</div>
		<div class="mb-3">
			<label class="form-label" for="parent"><g:message code='aclObjectIdentity.parent.label' default='Parent'/></label>
			<g:textField name='parent' class='form-control' maxlength='255' value='${parent}'/>
		</div>
		<div class="mb-3">
			<span class="form-label d-block"><g:message code='aclObjectIdentity.entriesInheriting.label' default='Entries Inheriting'/></span>
			<g:radioGroup name='entriesInheriting'
			              labels='${[message(code: "spring.security.ui.search.true"), message(code: "spring.security.ui.search.false"), message(code: "spring.security.ui.search.either")]}'
			              values='[1,-1,0]' value='${entriesInheriting ?: 0}'>
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
			<s2ui:sortableColumn property='aclClass.className' titleDefault='AclClass'/>
			<s2ui:sortableColumn property='objectId' titleDefault='Object ID'/>
			<s2ui:sortableColumn property='entriesInheriting' titleDefault='Entries Inheriting'/>
			<s2ui:sortableColumn property='owner.sid' titleDefault='Owner'/>
			<th><g:message code='parent.label' default='Parent'/></th>
		</tr>
		</thead>
		<tbody>
		<g:each in='${results}' var='oid'>
			<tr>
				<td><g:link action='edit' id='${oid.id}'>${oid.id}</g:link></td>
				<g:set var='oidAclClass' value='${uiPropertiesStrategy.getProperty(oid, "aclClass")}'/>
				<td><g:link action='edit' controller='aclClass' id='${oidAclClass.id}'>${uiPropertiesStrategy.getProperty(oidAclClass, 'className')}</g:link></td>
				<td>${uiPropertiesStrategy.getProperty(oid, 'objectId')}</td>
				<td><s2ui:formatBoolean bean='${oid}' name='entriesInheriting'/></td>
				<td>
					<g:set var='oidOwner' value='${uiPropertiesStrategy.getProperty(oid, "owner")}'/>
					<g:set var='isPrincipal' value='${uiPropertiesStrategy.getProperty(oidOwner, "principal")}'/>
					<g:set var='oidOwnerSid' value='${uiPropertiesStrategy.getProperty(oidOwner, "sid")}'/>
					<g:if test='${oidOwner && isPrincipal}'><g:link action='edit' controller='user' params='[username: oidOwnerSid]'>${oidOwnerSid}</g:link></g:if>
					<g:if test='${oidOwner && !isPrincipal}'><g:link action='edit' controller='role' params='[authority: oidOwnerSid]'>${oidOwnerSid}</g:link></g:if>
				</td>
				<td>
					<g:set var='oidParent' value='${uiPropertiesStrategy.getProperty(oid, 'parent')}'/>
					<g:if test='${oidParent}'><g:link action='edit' id='${oidParent.id}'>${oidParent.id}</g:link></g:if>
				</td>
			</tr>
		</g:each>
		</tbody>
	</table>
</div>
<s2ui:paginate total='${totalCount}'/>
</g:if>
</body>
</html>
