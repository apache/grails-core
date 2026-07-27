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
    <meta name="layout" content="${gspLayout ?: 'main'}"/>
    <title><g:message code='springSecurity.login.title'/></title>
</head>

<body>
<div class="container py-5">
    <div class="row justify-content-center">
        <div class="col-11 col-sm-9 col-md-7 col-lg-5 col-xl-4">
            <div class="card shadow-sm">
                <div class="card-body p-4 p-sm-5">
                    <h1 class="h4 card-title text-center mb-4"><g:message code='springSecurity.login.header'/></h1>

                    <g:flashMessages/>

                    <form action="${postUrl ?: '/login/authenticate'}" method="POST" id="loginForm" autocomplete="off">
                        <div class="mb-3">
                            <label for="username" class="form-label"><g:message code='springSecurity.login.username.label'/></label>
                            <input type="text" class="form-control" name="${usernameParameter ?: 'username'}" id="username"
                                   autocapitalize="none" autocomplete="username"/>
                        </div>

                        <div class="mb-3">
                            <label for="password" class="form-label"><g:message code='springSecurity.login.password.label'/></label>
                            <div class="input-group">
                                <input type="password" class="form-control" name="${passwordParameter ?: 'password'}" id="password"
                                       autocomplete="current-password"/>
                                <button class="btn btn-outline-secondary" type="button" id="passwordToggler"
                                        onclick="passwordDisplayToggle()" aria-pressed="false"
                                        aria-label="${message(code: 'springSecurity.login.password.toggle', default: 'Show or hide password')}">
                                    <i id="passwordTogglerIcon" class="bi bi-eye" aria-hidden="true"></i>
                                </button>
                            </div>
                        </div>

                        <div class="mb-3 form-check">
                            <input type="checkbox" class="form-check-input" name="${rememberMeParameter ?: 'remember-me'}"
                                   id="remember_me" <g:if test='${hasCookie}'>checked="checked"</g:if>/>
                            <label class="form-check-label" for="remember_me"><g:message code='springSecurity.login.remember.me.label'/></label>
                        </div>

                        <button type="submit" id="submit" class="btn btn-primary w-100"><g:message code='springSecurity.login.button'/></button>
                    </form>
                </div>
            </div>
        </div>
    </div>
</div>
<script type="text/javascript">
    document.addEventListener("DOMContentLoaded", function () {
        var form = document.forms['loginForm'];
        if (form) {
            form.elements['username'].focus();
        }
    });

    function passwordDisplayToggle() {
        var button = document.getElementById("passwordToggler");
        var icon = document.getElementById("passwordTogglerIcon");
        var field = document.getElementById("password");
        var hiding = field.type === "text";
        field.type = hiding ? "password" : "text";
        icon.classList.toggle("bi-eye", hiding);
        icon.classList.toggle("bi-eye-slash", !hiding);
        button.setAttribute("aria-pressed", String(!hiding));
    }
</script>
</body>
</html>
