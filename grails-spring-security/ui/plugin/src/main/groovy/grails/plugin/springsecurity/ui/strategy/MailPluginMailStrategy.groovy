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
package grails.plugin.springsecurity.ui.strategy

import groovy.util.logging.Slf4j

/**
 * To avoid depending on the mail plugin this class is registered as the 'uiMailStrategy'
 * Spring bean. Override the bean in resources.groovy to customize how emails are sent or
 * to use something other than the mail plugin.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Slf4j
class MailPluginMailStrategy implements MailStrategy {

	ErrorsStrategy uiErrorsStrategy

	def mailService

	def sendVerifyRegistrationMail(Map params) {
		sendMail params, 'sendVerifyRegistrationMail'
	}

	def sendForgotPasswordMail(Map params) {
		sendMail params, 'sendForgotPasswordMail'
	}

	protected sendMail(Map params, String methodName) {
		if (!mailService) {
			log.error "Cannot send mail: this implementation of MailStrategy depends " +
				"on the mail plugin's 'mailService' bean but it was not found; install the mail " +
				"plugin or register your own 'uiMailStrategy' bean to replace this one"
			return
		}

		try {
			mailService.sendMail {
				to      params.to
				from    params.from
				subject params.subject
				html    params.html
			}
		}
		catch (e) {
			uiErrorsStrategy.handleException e, null, null, this, methodName, null
		}
	}
}
