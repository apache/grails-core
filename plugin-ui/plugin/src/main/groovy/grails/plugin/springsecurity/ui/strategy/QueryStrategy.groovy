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

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
interface QueryStrategy {

	/**
	 * Build a closure that can be run inside a Criteria query that dynamically represents a proection
	 * into joined classes, e.g. for an AclEntry query:
	 *
	 *    aclObjectIdentity {
	 *       aclClass {
	 *          eq 'id', classId
	 *       }
	 *    }
	 *
	 *    you would call
	 *
	 *    buildProjection 'aclObjectIdentity.aclClass', 'eq', 'id', classId)
	 *
	 * @param path the projection path
	 * @param criterionMethod the inner method to call
	 * @param args the method args
	 * @return the closure
	 */
	Closure<?> buildProjection(String path, String criterionMethod, List args)

	/**
	 * Run a Criteria auery for the given class, method, and subcriterias.
	 * @param clazz the domain class
	 * @param criterias zero or more closures to invoke in the context of the criteria's builder
	 * @param paginateParams pagination params
	 */
	def runCriteria(Class<?> clazz, List<Closure<?>> criterias, Map paginateParams)
}
