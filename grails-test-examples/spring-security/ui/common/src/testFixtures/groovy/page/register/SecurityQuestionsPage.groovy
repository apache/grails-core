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
package page.register

import geb.module.TextInput

import page.LifecyclePage

class SecurityQuestionsPage extends LifecyclePage {

	static url = 'register/securityQuestions'
	static at = { title == 'Security Questions' }
	static content = {
		form { $('securityQuestionsForm') }
		question1 { $('#myAnswer1').module(TextInput) }
		question2 { $('#myAnswer2').module(TextInput) }
		submitBtn { $('a', id: 'submit') }
	}

	def <T extends LifecyclePage> T  submitAnswer(String answer1, String answer2, Class<T> expectedPageType) {
		if (answer1) question1.text = answer1
		if (answer2) question2.text = answer2
		submitBtn.click()
		T page = browser.at(expectedPageType)
		waitFor { page.loaded }
		page
	}
}
