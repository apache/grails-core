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
package startupprogress

import org.springframework.web.util.HtmlUtils

import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
class CatalogController {

    CatalogService catalogService

    def index() {
        List<String> items = catalogService.items
        render(contentType: 'text/html', text: """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>startup-progress</title><link rel="icon" href="data:,"></head>
<body>
<h1>startup-progress is running</h1>
<p id="seeded">BootStrap seeded ${items.size()} catalog items: ${HtmlUtils.htmlEscape(items.join(', '))}</p>
</body></html>
""")
    }
}
