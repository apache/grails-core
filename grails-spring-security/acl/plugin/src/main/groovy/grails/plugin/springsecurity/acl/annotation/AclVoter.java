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
package grails.plugin.springsecurity.acl.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.acls.domain.BasePermission;

/**
 * Annotation for Controllers or Services at the class level or per-action/per-method,
 * defining what roles and/or ACL voters are required for the entire controller or action.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface AclVoter {

	/**
	 * The bean name of the associated Voter.
	 * @return  the name
	 */
	String name();

   /**
    * The config attribute, e.g. <code>ACL_REPORT_WRITE</code>.
    * @return  the attribute
    */
   String configAttribute();

   /**
    * The {@link BasePermission} constant names that are required.
    * Defaults to {@link BasePermission#READ}.
    *
    * @return  the names
    */
   String[] permissions() default { "READ" };
}
