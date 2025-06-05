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

import com.testapp.TestUser
import grails.gorm.transactions.Transactional
import org.springframework.security.access.annotation.Secured

@Transactional
@Secured('permitAll')
class HackController {

	def userCache

	def getSessionValue(String name) {
		def value = session[name]
		render value ? value.toString() : ''
	}

	def getUserProperty(String user, String propName) {
		render TestUser.findByUsername(user)."$propName"
	}

	def setUserProperty() {
		def user = TestUser.findByUsername(params.user)
		user.properties = params
		user.save(flush: true)
		userCache.removeUserFromCache user.username
		render 'setUserProperty: OK'
	}

	def blankPage() {
		render ''
	}
}
