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
package grails.plugin.springsecurity.annotation

import java.lang.annotation.Documented
import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Annotation for Controllers at the class level or per-action, defining what roles
 * are required for the entire controller or action.
 *
 * @author Burt Beckwith
 */
@Target([ElementType.METHOD, ElementType.TYPE])
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@interface Secured {

	/**
	 * Default value for httpMethod().
	 */
	String ANY_METHOD = 'ANY'

	/**
	 * Defines the security configuration attributes (e.g. ROLE_USER, ROLE_ADMIN, IS_AUTHENTICATED_REMEMBERED, etc.)
	 * @return the names of the roles, expressions, and tokens
	 */
	String[] value() default []

	/**
	 * Optional attribute to specify the HTTP method required.
	 * @return the method
	 */
	String httpMethod() default 'ANY'

	/**
	 * Optional attribute to specify a closure that will be evaluated to decide if access should be allowed.
	 * @return the closure class
	 */
	Class<?> closure() default Secured
}
