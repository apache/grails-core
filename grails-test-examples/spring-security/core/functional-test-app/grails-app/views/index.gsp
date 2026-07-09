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
	<title>Welcome to Grails</title>
</head>

<body>
<h1>Available Controllers:</h1>
<ul>
    <g:each var="c" in="${grailsApplication.controllerClasses.sort { it.fullName } }">
        <li class="controller"><g:link controller="${c.logicalPropertyName}" action='${c.defaultAction}'>${c.fullName}</g:link></li>
    </g:each>
</ul>
<h1>Application Status</h1>
<ul>
	<li>Environment: ${grails.util.Environment.current.name}</li>
	<li>App profile: ${grailsApplication.config.grails?.profile}</li>
	<li>App version: <g:meta name="info.app.version"/></li>
	<li>Grails version: <g:meta name="info.app.grailsVersion"/></li>
	<li>Groovy version: ${GroovySystem.getVersion()}</li>
	<li>JVM version: ${System.getProperty('java.version')}</li>
	<li>Reloading active: ${grails.util.Environment.reloadingAgentEnabled}</li>
</ul>
<h1>Artefacts</h1>
<ul>
	<li>Controllers: ${grailsApplication.controllerClasses.size()}</li>
	<li>Domains: ${grailsApplication.domainClasses.size()}</li>
	<li>Services: ${grailsApplication.serviceClasses.size()}</li>
	<li>Tag Libraries: ${grailsApplication.tagLibClasses.size()}</li>
</ul>
<h1>Installed Plugins</h1>
<ul>
	<g:each var="plugin" in="${applicationContext.getBean('pluginManager').allPlugins}">
	<li>${plugin.name} - ${plugin.version}</li>
	</g:each>
</ul>
</body>
</html>
