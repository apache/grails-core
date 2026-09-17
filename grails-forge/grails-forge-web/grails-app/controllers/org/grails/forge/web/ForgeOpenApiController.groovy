/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.forge.web

import grails.web.Controller
import groovy.json.JsonOutput

@Controller
class ForgeOpenApiController {

    static allowedMethods = [document: 'GET', swaggerUi: 'GET', rapidoc: 'GET']

    def document() {
        render text: JsonOutput.toJson(ForgeOpenApiDocument.spec()), contentType: 'application/json'
    }

    def swaggerUi() {
        render text: """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <title>Grails Forge API</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css"/>
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
  <script>
    window.ui = SwaggerUIBundle({ url: '/v3/api-docs', dom_id: '#swagger-ui' })
  </script>
</body>
</html>
""", contentType: 'text/html'
    }

    def rapidoc() {
        render text: """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <title>Grails Forge API</title>
  <script type="module" src="https://unpkg.com/rapidoc/dist/rapidoc-min.js"></script>
</head>
<body>
  <rapi-doc spec-url="/v3/api-docs"></rapi-doc>
</body>
</html>
""", contentType: 'text/html'
    }
}
