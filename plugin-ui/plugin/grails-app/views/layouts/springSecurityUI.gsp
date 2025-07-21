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
<!doctype html>
<html class="no-js" lang="">
<head>
	<meta charset="utf-8">
	<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
	<meta http-equiv="x-ua-compatible" content="ie=edge">
	<meta name="viewport" content="width=device-width, initial-scale=1">
	<s2ui:stylesheet src='spring-security-ui'/>
<g:layoutHead/>
</head>
<body>
<div>
	<div>
		<ul class="jd_menu jd_menu_slate">
			<s2ui:menu controller='user'/>
			<s2ui:menu controller='role'/>
			<g:if test='${securityConfig.securityConfigType?.toString() == 'Requestmap'}'><s2ui:menu controller='requestmap'/></g:if>
			<g:if test='${securityConfig.rememberMe.persistent}'><s2ui:menu controller='persistentLogin' searchOnly='true'/></g:if>
			<s2ui:menu controller='registrationCode' searchOnly='true'/>
			<g:if test='${applicationContext.pluginManager.hasGrailsPlugin('springSecurityAcl')}'>
			<li><a class="accessible"><g:message code='spring.security.ui.menu.acl'/></a>
				<ul>
					<s2ui:menu controller='aclClass' submenu='true'/>
					<s2ui:menu controller='aclSid' submenu='true'/>
					<s2ui:menu controller='aclObjectIdentity' submenu='true'/>
					<s2ui:menu controller='aclEntry' submenu='true'/>
				</ul>
			</li>
			</g:if>
			<g:if test='${securityConfig.ui.forgotPassword?.forgotPasswordExtraValidation?.size() > 0 }'>
				<s2ui:menu controller='${securityConfig.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName.substring(securityConfig.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName.lastIndexOf('.') + 1)}' showList="true" noSearch="true" />
			</g:if>
			<li><a class="accessible"><g:message code='spring.security.ui.menu.securityInfo'/></a>
				<ul>
					<s2ui:menu controller='securityInfo' itemAction='config'/>
					<s2ui:menu controller='securityInfo' itemAction='mappings'/>
					<s2ui:menu controller='securityInfo' itemAction='currentAuth'/>
					<s2ui:menu controller='securityInfo' itemAction='usercache'/>
					<s2ui:menu controller='securityInfo' itemAction='filterChains'/>
					<s2ui:menu controller='securityInfo' itemAction='logoutHandlers'/>
					<s2ui:menu controller='securityInfo' itemAction='voters'/>
					<s2ui:menu controller='securityInfo' itemAction='providers'/>
					<s2ui:menu controller='securityInfo' itemAction='secureChannel'/>
				</ul>
			</li>
		</ul>
		<div id='s2ui_header_body'>
			<div id='s2ui_header_title'><g:message code='spring.security.ui.defaultTitle'/></div>
			<g:render template='/includes/ajaxLogin'/>
		</div>
	</div>
	<div id="s2ui_main">
		<div id="s2ui_content">
			<p/>
			<g:layoutBody/>
		</div>
	</div>
</div>
<asset:javascript src='spring-security-ui.js'/>
<s2ui:showFlash/>
<s2ui:deferredScripts/>
</body>
</html>
