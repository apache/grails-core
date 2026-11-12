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

package com.testacl

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException

class UrlMappings {

	static mappings = {
		"/$controller/$action?/$id?(.$format)?"{}

		"/"(view: '/index')

		"403"(controller: 'errors', action: 'error403')
		"404"(controller: 'errors', action: 'error404')
		"500"(controller: 'errors', action: 'error500')
		"500"(view: 'errors/error403', exception: AccessDeniedException)
		"500"(controller: 'errors', action: 'error404', exception: NotFoundException)
	}
}
