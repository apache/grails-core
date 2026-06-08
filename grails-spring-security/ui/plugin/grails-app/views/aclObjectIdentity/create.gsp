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
	<s2ui:title messageCode='default.create.label' entityNameMessageCode='aclObjectIdentity.label' entityNameDefault='AclObjectIdentity'/>
</head>
<body>
<div class="body">
	<s2ui:formContainer type='save' beanType='aclObjectIdentity' focus='objectId'>
		<s2ui:form useToken="true">
			<div class="dialog">
				<br/>
				<table>
					<tbody>
					<s2ui:selectRow name='aclClass.id' from='${classes}' labelCodeDefault='AclClass' optionValue='className' noSelection="['null': '']"/>
					<s2ui:textFieldRow name='objectId' size='50' labelCodeDefault='Object ID'/>
					<s2ui:selectRow name='owner.id' from='${sids}' labelCodeDefault='Owner' optionValue='sid' noSelection="['null': '']"/>
					<s2ui:textFieldRow name='parent.id' size='50' labelCodeDefault='Parent'/>
					<s2ui:checkboxRow name='entriesInheriting' labelCodeDefault='Entries Inheriting'/>
					<tr><td>&nbsp;</td></tr>
					<tr class="prop"><td valign="top"><s2ui:submitButton/></td></tr>
					</tbody>
				</table>
			</div>
		</s2ui:form>
	</s2ui:formContainer>
</div>
</body>
</html>
