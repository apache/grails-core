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
	<s2ui:title messageCode='default.create.label' entityNameMessageCode='user.label' entityNameDefault='User'/>
</head>
<body>
<h3><g:message code='default.create.label' args='[entityName]'/></h3>
<s2ui:form type='save' beanName='user' focus='username' useToken='true'>
	<s2ui:tabs elementId='tabs' height='375' data='${tabData}'>
		<s2ui:tab name='userinfo' height='280'>
			<table>
				<tbody>
				<s2ui:textFieldRow name='username' labelCodeDefault='Username'/>
				<s2ui:passwordFieldRow name='password' labelCodeDefault='Password'/>
				<s2ui:checkboxRow name='enabled' labelCodeDefault='Enabled'/>
				<s2ui:checkboxRow name='accountExpired' labelCodeDefault='Account Expired'/>
				<s2ui:checkboxRow name='accountLocked' labelCodeDefault='Account Locked'/>
				<s2ui:checkboxRow name='passwordExpired' labelCodeDefault='Password Expired'/>
				</tbody>
			</table>
		</s2ui:tab>
		<s2ui:tab name='roles' height='280'>
		<g:each var='role' in='${authorityList}'>
			<div>
				<g:set var='authority' value='${uiPropertiesStrategy.getProperty(role, 'authority')}'/>
				<g:checkBox name='${authority}'/>
				<g:link controller='role' action='edit' id='${role.id}'>${authority}</g:link>
			</div>
		</g:each>
		</s2ui:tab>
	</s2ui:tabs>
	<div style='float:left; margin-top: 10px;'>
		<s2ui:submitButton/>
	</div>
</s2ui:form>
</body>
</html>
