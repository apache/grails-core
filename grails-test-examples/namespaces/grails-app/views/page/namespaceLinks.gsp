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
<html>
<head>
    <meta name="layout" content="main"/>
    <title>${pageTitle}</title>
</head>
<body>
    <div id="content" role="main">
        <h1>${pageTitle}</h1>

        <g:link elementId="bookLink" controller="book" action="index">Book Link</g:link>
        <a id="bookCreateLink" href="${createLink(controller: 'book', action: 'list')}">Book Create Link</a>
        <g:link elementId="reportLink" controller="report" action="index">Report Link</g:link>
        <g:link elementId="currentPageLink" controller="page" action="index">Current Page Link</g:link>
        <g:link elementId="frontendPageLink" controller="page" action="index" namespace="frontend">Frontend Page Link</g:link>
        <g:link elementId="rootReportLink" controller="report" action="index" namespace="${null}">Root Report Link</g:link>
        <app:bookLink id="customBookLink"/>

        <g:form name="bookForm" controller="book" action="save">
            <g:formActionSubmit id="bookFormActionSubmit" controller="book" action="alternateSave" value="Submit To Book"/>
        </g:form>

        <div id="bookPagination">
            <g:paginate controller="book" action="list" total="30" max="10"/>
        </div>

        <table>
            <thead>
                <tr>
                    <g:sortableColumn id="pageSortable" property="title" action="list" title="Title"/>
                </tr>
            </thead>
        </table>

        <div id="bookInclude">
            <g:include controller="book" action="included"/>
        </div>
    </div>
</body>
</html>
