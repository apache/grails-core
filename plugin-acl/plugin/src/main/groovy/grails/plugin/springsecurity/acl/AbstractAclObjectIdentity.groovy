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
package grails.plugin.springsecurity.acl

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Abstract base class for the AclObjectIdentity domain class. The default implementation
 * assumes a long objectId but custom implementations may wish to use other PK types.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@EqualsAndHashCode(includes=['aclClass', 'parent', 'owner', 'entriesInheriting'])
@ToString(includeNames=true)
abstract class AbstractAclObjectIdentity implements Serializable {

	AclClass aclClass
	AclObjectIdentity parent
	AclSid owner
	boolean entriesInheriting

	static constraints = {
		parent nullable: true
		owner nullable: true
	}
}
