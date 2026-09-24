<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
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
        <title>Sign up</title>
    </head>
    <body>
        <div class="card shadow-sm">
            <div class="card-body">
                <h2 class="card-title h5">Sign up</h2>
                <p class="card-subtitle text-body-secondary mb-4">
                    A Spring MVC form, bound and validated by Spring, written with Spring's form tag
                    library - a JSP tag library - inside a GSP page.
                </p>
                <form:form modelAttribute="person" method="post" cssClass="row g-3">
                    <div class="col-12">
                        <form:label path="name" cssClass="form-label">Name</form:label>
                        <form:input path="name" cssClass="form-control" cssErrorClass="form-control is-invalid"/>
                        <form:errors path="name" cssClass="field-error invalid-feedback d-block"/>
                        <div class="form-text">Between 2 and 30 characters.</div>
                    </div>
                    <div class="col-12 col-sm-6">
                        <form:label path="age" cssClass="form-label">Age</form:label>
                        <form:input path="age" type="number" cssClass="form-control" cssErrorClass="form-control is-invalid"/>
                        <form:errors path="age" cssClass="field-error invalid-feedback d-block"/>
                        <div class="form-text">18 or over.</div>
                    </div>
                    <div class="col-12">
                        <button type="submit" class="btn btn-primary">
                            <i class="bi bi-check2-circle me-1"></i>Submit
                        </button>
                    </div>
                </form:form>
            </div>
        </div>

        <%-- A second layout, applied to a block of this page rather than to the page. --%>
        <g:applyLayout name="sample">
            Text to decorate.
        </g:applyLayout>
    </body>
</html>
