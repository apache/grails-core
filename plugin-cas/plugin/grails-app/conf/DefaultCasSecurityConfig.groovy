/* Copyright 2006-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
security {
	cas {
		active = true
		loginUri = null // must be set, e.g. '/login'
		sendRenew = false
		serviceUrl = null // must be set, e.g. 'http://localhost:8080/myapp/login/cas'
		serverUrlPrefix = null // must be set, e.g. 'http://localhost:9090/cas'
		serverUrlEncoding = 'UTF-8'
		key = 'grails-spring-security-cas'
		artifactParameter = 'ticket'
		serviceParameter = 'service'
		filterProcessesUrl = '/login/cas'
		proxyCallbackUrl = null // should be set, e.g. 'http://localhost:8080/myapp/secure/receptor'
		proxyReceptorUrl = null // should be set, e.g. '/secure/receptor'
		useSingleSignout = true
	}
}
