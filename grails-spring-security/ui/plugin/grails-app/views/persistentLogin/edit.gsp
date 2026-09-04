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
	<s2ui:title messageCode='default.edit.label' entityNameMessageCode='persistentLogin.label' entityNameDefault='PersistentLogin'/>
</head>
<body>
<s2ui:formContainer type='update' beanType='persistentLogin' focus='token' width='40rem'>
	<s2ui:form idName='series' useToken="true">
		<div class="mb-3">
			<span class="form-label d-block">${message(code: 'persistentLogin.series.label', default: 'Series')}</span>
			<p class="form-control-plaintext mb-0">${persistentLogin.series}</p>
		</div>
		<div class="mb-3">
			<span class="form-label d-block">${message(code: 'persistentLogin.username.label', default: 'Username')}</span>
			<p class="form-control-plaintext mb-0"><g:link action='edit' controller='user' params='[username: persistentLogin.username]'>${persistentLogin.username}</g:link></p>
		</div>
		<s2ui:textFieldRow name='token' labelCodeDefault='Token'/>
		<s2ui:dateFieldRow name='lastUsed' labelCodeDefault='Last Used'/>
		<div class="d-flex gap-2">
			<s2ui:submitButton/>
			<g:if test='${persistentLogin}'><s2ui:deleteButton/></g:if>
		</div>
	</s2ui:form>
</s2ui:formContainer>
<g:if test='${persistentLogin}'><s2ui:deleteButtonForm instanceId='${persistentLogin.series}' useToken="true"/></g:if>
</body>
</html>
