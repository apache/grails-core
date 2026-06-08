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

import org.springframework.transaction.TransactionStatus

/**
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
interface PropertiesStrategy {

	Map<Class<?>, Map<String, String>> findClassMappings()

	/**
	 * Gets the property from the instance. paramName is a hard-coded 'params' name
	 * (e.g. 'username') and the actual property name is looked up from the security
	 * config in case it was overridden.
	 *
	 * @param instance the instance
	 * @param paramName the 'params' name
	 * @return the value or null if it's not callable
	 * @throws grails.plugin.springsecurity.ui.InvalidValueException if there's a problem getting the property
	 */
	def getProperty(instance, String paramName)

	/**
	 * Sets the properties in the instance (creating it first if instanceOrClass is a Class) from the
	 * provided map. The keys are hard-coded 'params' names (e.g. 'username') and the actual property
	 * names are looked up from the security config in case they've been overridden.
	 *
	 * @param data the data
	 * @param instanceOrClass an instance or Class
	 * @param the current TransactionStatus if in a transaction, to pass to the error strategy
	 * @return the instance passed or the new instance created from a Class
	 * @throws grails.plugin.springsecurity.ui.InvalidValueException if there's a problem setting a property
	 */
	def setProperties(Map data, instanceOrClass, TransactionStatus transactionStatus)

	/**
	 * Lookup the property name based on the 'params' name, e.g. the overridden name for 'username'.
	 * The controller name is used since there's no instance to know its class.
	 *
	 * @param paramName the 'params' name
	 * @param controllerName the taglib 'controllerName' property
	 * @return the actual property name in the domain class
	 */
	String paramNameToPropertyName(String paramName, String controllerName)
}
