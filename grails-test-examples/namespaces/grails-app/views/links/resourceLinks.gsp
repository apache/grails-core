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
<%@ page import="namespaces.Assessment" %>
<!doctype html>
<html>
<head>
    <title>Resource Links</title>
</head>
<body>
<a id="assessmentShowLink" href="${createLink(resource: assessment, action: 'show')}">Assessment</a>
<a id="assessmentEditLink" href="${createLink(resource: assessment, action: 'edit')}">Edit Assessment</a>
<a id="assessmentNoActionLink" href="${createLink(resource: assessment)}">Assessment, no action</a>
<a id="assessmentGetLink" href="${createLink(resource: assessment, method: 'GET')}">Assessment, GET</a>
<a id="assessmentExplicitLink" href="${createLink(resource: assessment, action: 'show', controller: 'assessment')}">Assessment, explicit controller</a>
<a id="assessmentClassLink" href="${createLink(resource: Assessment, action: 'show', id: assessment.id)}">Assessment, by class</a>
<a id="assessmentIndexLink" href="${createLink(resource: Assessment, action: 'index')}">Assessments</a>
<g:link elementId="assessmentTagLink" resource="${assessment}" action="show">Assessment, tag</g:link>
<g:form name="assessmentForm" resource="${assessment}" action="update" method="PUT"></g:form>
<a id="filmShowLink" href="${createLink(resource: film, action: 'show')}">Film</a>
<a id="gadgetShowLink" href="${createLink(resource: gadget, action: 'show')}">Gadget</a>
<a id="catalogItemShowLink" href="${createLink(resource: catalogItem, action: 'show')}">Catalog item</a>
<a id="archiveItemShowLink" href="${createLink(resource: archiveItem, action: 'show')}">Archive item</a>
<a id="screeningFilmLink" href="${createLink(resource: screening?.film, action: 'show')}">Screening film</a>
<a id="authorLink" href="${createLink(controller: 'author', action: 'index')}">Author</a>
<a id="homeLink" href="${createLink(controller: 'home', action: 'index')}">Home</a>
<a id="unknownLink" href="${createLink(controller: 'nowhere', action: 'list')}">Unknown</a>
</body>
</html>
