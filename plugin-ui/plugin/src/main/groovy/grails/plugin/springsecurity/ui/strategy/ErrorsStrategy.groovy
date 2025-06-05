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
interface ErrorsStrategy {

	/**
	 * @param t the exception
	 * @param bean a domain class or @Validateable instance
	 * @param properties the properties being used to create or update the instance (or null if deleting)
	 * @param source caller, to help determine how to handle the errors (typically the ui service or a controller)
	 * @param operation where the problem happened, will typically be the method name
	 * @param transactionStatus the current transactionStatus (to call setRollbackOnly(), etc.)
	 */
	void handleException(Throwable t, bean, Map properties, source, String operation, TransactionStatus transactionStatus)

	/**
	 * @param bean a domain class or @Validateable instance
	 * @param source caller, to help determine how to handle the errors (typically the ui service or a controller)
	 * @param operation where the problem happened, will typically be the method name
	 * @param transactionStatus the current transactionStatus (to call setRollbackOnly(), etc.)
	 */
	void handleValidationErrors(bean, source, String operation, TransactionStatus transactionStatus)
}
