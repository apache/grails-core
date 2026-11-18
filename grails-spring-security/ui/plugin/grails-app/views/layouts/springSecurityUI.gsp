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
<%--
  A thin sub-layout: plugin pages render through the host application's own
  layout (grails.plugin.springsecurity.ui.gsp.parentLayout, default 'main'),
  inheriting its theme (including dark mode), locale selector, branding and
  assets. The security menu and the login state are contributed to the host
  navbar through the 'nav' and 'navActions' content blocks, which the
  create-app layout renders with <g:pageProperty name="page.nav"/> and
  <g:pageProperty name="page.navActions"/>.
  --%>
<g:set var="s2uiPageTitle" value="${g.layoutTitle()}"/>
<g:applyLayout name="${securityConfig.ui.gsp.parentLayout ?: 'main'}" params="[title: s2uiPageTitle]">
<html>
<head>
	<g:layoutHead/>
</head>
<body>
<content tag="nav">
	<s2ui:menu controller='user'/>
	<s2ui:menu controller='role'/>
	<g:if test='${securityConfig.securityConfigType?.toString() == 'Requestmap'}'><s2ui:menu controller='requestmap'/></g:if>
	<g:if test='${securityConfig.rememberMe.persistent}'><s2ui:menu controller='persistentLogin' searchOnly='true'/></g:if>
	<s2ui:menu controller='registrationCode' searchOnly='true'/>
	<g:if test='${applicationContext.pluginManager.hasGrailsPlugin('springSecurityAcl')}'>
	<li class="nav-item dropdown">
		<a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false"><g:message code='spring.security.ui.menu.acl'/></a>
		<ul class="dropdown-menu">
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
	<li class="nav-item dropdown">
		<a class="nav-link dropdown-toggle" href="#" role="button" data-bs-toggle="dropdown" aria-expanded="false"><g:message code='spring.security.ui.menu.securityInfo'/></a>
		<ul class="dropdown-menu">
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
</content>
<content tag="navActions">
	<li class="nav-item d-flex align-items-center gap-2">
		<g:render template='/includes/ajaxLogin'/>
	</li>
</content>
<g:layoutBody/>
<asset:javascript src='spring-security-ui.js'/>
<s2ui:deferredScripts/>
</body>
</html>
</g:applyLayout>
