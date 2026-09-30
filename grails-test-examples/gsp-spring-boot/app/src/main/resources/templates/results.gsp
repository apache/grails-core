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
        <title>Signed up</title>
    </head>
    <body>
        <div class="card shadow-sm">
            <div class="card-body">
                <h2 class="card-title h5 d-flex align-items-center gap-2">
                    <i class="bi bi-check-circle-fill text-success"></i>Congratulations ${session.getAttribute("person").name}!
                </h2>
                <p class="card-text text-body-secondary mb-4">You are old enough to sign up for this site.</p>
                <a class="btn btn-outline-primary" href="${request.contextPath}/">
                    <i class="bi bi-arrow-left me-1"></i>Back to the form
                </a>
            </div>
        </div>
    </body>
</html>
