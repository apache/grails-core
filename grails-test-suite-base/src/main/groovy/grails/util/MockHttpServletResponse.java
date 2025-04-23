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
package grails.util;

/**
 * Subclass Spring's MockHttpServletResponse to tag the methods that have been deprecated in
 * the Servlet API.
 * <p>
 * Because Spring's MockHttpServletResponse doesn't tag these methods as deprecated, the
 * compiler outputs noisy warnings complaining that we're using deprecated methods if we use
 * the raw MockHttpServletResponse from Spring.  By subclassing Spring's
 * MockHttpServletResponse and tagging the methods as deprecated, we acknowledge to the
 * compiler that these methods are deprecated, and we silence the compiler warnings.
 * <p>
 * Created: 08-Feb-2008
 *
 * @deprecated as of 7.0 in favor of using {@link org.springframework.mock.web.MockHttpServletResponse} directly.
 */
@Deprecated(forRemoval = true)
class MockHttpServletResponse extends org.springframework.mock.web.MockHttpServletResponse {}
