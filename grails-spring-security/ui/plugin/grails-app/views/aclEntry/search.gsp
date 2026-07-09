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
<div>
	<s2ui:formContainer type='search' beanType='aclEntry' focus='aclObjectIdentity' height='550'>
		<s2ui:searchForm colspan='4'>
			<tr>
				<td><g:message code='aclEntry.aclObjectIdentity.label' default='AclObjectIdentity'/>:</td>
				<td colspan='3'><g:textField name='aclObjectIdentity.id' id='aclObjectIdentity' size='50' maxlength='255' value='${aclObjectIdentity}'/></td>
			</tr>
			<tr>
				<td><g:message code='aclEntry.aceOrder.label' default='Ace Order'/>:</td>
				<td colspan='3'><g:textField name='aceOrder' size='50' maxlength='255' value='${aceOrder}'/></td>
			</tr>
			<tr>
				<td><g:message code='aclEntry.sid.label' default='SID'/>:</td>
				<td colspan='3'>
					<g:select name='sid.id' id='sid' from='${sids}' optionKey='id' optionValue='sid' value='${sid}' noSelection="['null': 'All']"/>
				</td>
			</tr>
			<tr>
				<td><g:message code='aclEntry.mask.label' default='Mask'/>:</td>
				<td colspan='3'><g:textField name='mask' size='50' maxlength='255' value='${mask}'/></td>
			</tr>
			<tr>
				<td>&nbsp;</td>
				<td><g:message code='spring.security.ui.search.true'/></td>
				<td><g:message code='spring.security.ui.search.false'/></td>
				<td><g:message code='spring.security.ui.search.either'/></td>
			</tr>
			<tr>
				<td><g:message code='aclEntry.granting.label' default='Granting'/>:</td>
				<g:radioGroup name='granting' labels="['','','']" values='[1,-1,0]' value='${granting ?: 0}'>
					<td><%=it.radio%></td>
				</g:radioGroup>
			</tr>
			<tr>
				<td><g:message code='aclEntry.auditSuccess.label' default='Audit Success'/>:</td>
				<g:radioGroup name='auditSuccess' labels="['','','']" values='[1,-1,0]' value='${auditSuccess ?: 0}'>
					<td><%=it.radio%></td>
				</g:radioGroup>
			</tr>
			<tr>
				<td><g:message code='aclEntry.auditFailure.label' default='Audit Failure'/>:</td>
				<g:radioGroup name='auditFailure' labels="['','','']" values='[1,-1,0]' value='${auditFailure ?: 0}'>
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
			<g:each in='${results}' status='i' var='entry'>
				<tr class="${(i % 2) == 0 ? 'odd' : 'even'}">
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
</div>
</body>
</html>
