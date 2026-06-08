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
<div>
	<s2ui:formContainer type='search' beanType='aclObjectIdentity' focus='objectId'>
		<s2ui:searchForm colspan='4'>
			<tr>
				<td><g:message code='aclObjectIdentity.aclClass.label' default='AclClass'/>:</td>
				<td colspan='3'>
					<g:select name='aclClass.id' id='aclClass' from='${classes}' optionKey='id' optionValue='className' value='${aclClass}' noSelection="['null': 'All']"/>
				</td>
			</tr>
			<tr>
				<td><g:message code='aclObjectIdentity.objectId.label' default='Object ID'/>:</td>
				<td colspan='3'><g:textField name='objectId' size='50' maxlength='255' value='${objectId}'/></td>
			</tr>
			<tr>
				<td><g:message code='aclObjectIdentity.owner.label' default='Owner'/>:</td>
				<td colspan='3'>
					<g:select name='owner.id' id='owner' from='${sids}' optionKey='id' optionValue='sid' value='${pageScope.owner}' noSelection="['null': 'All']"/>
				</td>
			</tr>
			<tr>
				<td><g:message code='aclObjectIdentity.parent.label' default='Parent'/>:</td>
				<td colspan='3'><g:textField name='parent' size='50' maxlength='255' value='${parent}'/></td>
			</tr>
			<tr>
				<td>&nbsp;</td>
				<td><g:message code='spring.security.ui.search.true'/></td>
				<td><g:message code='spring.security.ui.search.false'/></td>
				<td><g:message code='spring.security.ui.search.either'/></td>
			</tr>
			<tr>
				<td><g:message code='aclObjectIdentity.entriesInheriting.label' default='Entries Inheriting'/>:</td>
				<g:radioGroup name='entriesInheriting' labels="['','','']" values='[1,-1,0]' value='${entriesInheriting ?: 0}'>
					<td><%=it.radio%></td>
				</g:radioGroup>
			</tr>
		</s2ui:searchForm>
	</s2ui:formContainer>
	<g:if test='${searched}'>
	<div class="list">
		<table>
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
			<g:each in='${results}' status='i' var='oid'>
				<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
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
						&nbsp;
					</td>
					<td>
						<g:set var='oidParent' value='${uiPropertiesStrategy.getProperty(oid, 'parent')}'/>
						<g:if test='${oidParent}'><g:link action='edit' id='${oidParent.id}'>${oidParent.id}</g:link></g:if>
						&nbsp;
					</td>
				</tr>
			</g:each>
			</tbody>
		</table>
	</div>
	<s2ui:paginate total='${totalCount}'/>
	</g:if>
</div>
</body>
</html>
