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

import functionaltests.encoding.EncodingRecordingFilter
import functionaltests.encoding.ParameterReadingFilter
import functionaltests.interceptors.StaticInitFailureFilter
import org.example.MyBean
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.core.Ordered
// Place your Spring DSL code here
beans = {
	myBean(MyBean)
	staticInitFailureFilter(FilterRegistrationBean) {
		filter = new StaticInitFailureFilter()
		urlPatterns = ['/staticInitFailure/*']
	}
	parameterReadingFilter(FilterRegistrationBean) {
		filter = new ParameterReadingFilter()
		urlPatterns = ['/encoding/*']
		order = Ordered.HIGHEST_PRECEDENCE + 1
	}
	encodingRecordingFilter(FilterRegistrationBean) {
		filter = new EncodingRecordingFilter()
		urlPatterns = ['/encoding/*']
		order = Ordered.HIGHEST_PRECEDENCE
	}
}
