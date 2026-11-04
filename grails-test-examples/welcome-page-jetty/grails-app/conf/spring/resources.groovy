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

import jakarta.servlet.DispatcherType

import org.springframework.boot.web.servlet.FilterRegistrationBean

import welcomepagejetty.ContainerFilterInitializer
import welcomepagejetty.DisabledFilter
import welcomepagejetty.ErrorOnlyFilter
import welcomepagejetty.ServletMappedFilter
import welcomepagejetty.UrlFilter

beans = {
    urlFilter(FilterRegistrationBean) {
        filter = new UrlFilter()
        urlPatterns = ['/*']
        order = 100
    }
    servletMappedFilter(FilterRegistrationBean) {
        filter = new ServletMappedFilter()
        servletRegistrationBeans = [ref('dispatcherServletRegistration')]
        order = 200
    }
    errorOnlyFilter(FilterRegistrationBean) {
        filter = new ErrorOnlyFilter()
        urlPatterns = ['/*']
        dispatcherTypes = EnumSet.of(DispatcherType.ERROR)
        order = 400
    }
    disabledFilter(FilterRegistrationBean) {
        filter = new DisabledFilter()
        enabled = false
    }
    containerFilterInitializer(ContainerFilterInitializer)
}
