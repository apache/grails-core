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
	<s2ui:title messageCode='default.create.label' entityNameMessageCode='aclEntry.label' entityNameDefault='AclEntry'/>
</head>
<body>
<s2ui:formContainer type='save' beanType='aclEntry' focus='aclObjectIdentity' width='40rem'>
	<s2ui:form useToken="true">
		<s2ui:textFieldRow name='aclObjectIdentity.id' labelCodeDefault='AclObjectIdentity'/>
		<s2ui:textFieldRow name='aceOrder' labelCodeDefault='Ace Order'/>
		<s2ui:selectRow name='sid.id' from='${sids}' labelCodeDefault='SID' optionValue='sid' noSelection="['null': '']"/>
		<s2ui:textFieldRow name='mask' labelCodeDefault='Mask'/>
		<s2ui:checkboxRow name='granting' labelCodeDefault='Granting'/>
		<s2ui:checkboxRow name='auditSuccess' labelCodeDefault='Audit Success'/>
		<s2ui:checkboxRow name='auditFailure' labelCodeDefault='Audit Failure'/>
		<s2ui:submitButton/>
	</s2ui:form>
</s2ui:formContainer>
</body>
</html>
