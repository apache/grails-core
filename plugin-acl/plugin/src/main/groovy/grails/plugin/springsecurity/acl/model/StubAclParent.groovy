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
package grails.plugin.springsecurity.acl.model

import groovy.transform.CompileStatic
import org.springframework.security.acls.domain.ObjectIdentityImpl
import org.springframework.security.acls.model.AccessControlEntry
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Permission
import org.springframework.security.acls.model.Sid

/**
 * Copied from the <code>BasicLookupStrategy</code> private inner class.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
@CompileStatic
class StubAclParent implements Acl {

	private static final long serialVersionUID = 1

	final Long id

	/**
	 * Constructor.
	 * @param id  the id
	 */
	StubAclParent(Long id) {
		this.id = id
	}

	List<AccessControlEntry> getEntries() {
		throw new UnsupportedOperationException('Stub only')
	}

	ObjectIdentity getObjectIdentity() {
		return new ObjectIdentityImpl(getClass(), 0)
	}

	Sid getOwner() {
		throw new UnsupportedOperationException('Stub only')
	}

	Acl getParentAcl() {
		throw new UnsupportedOperationException('Stub only')
	}

	boolean isEntriesInheriting() {
		throw new UnsupportedOperationException('Stub only')
	}

	boolean isGranted(List<Permission> permission, List<Sid> sids, boolean administrativeMode) {
		throw new UnsupportedOperationException('Stub only')
	}

	boolean isSidLoaded(List<Sid> sids) {
		throw new UnsupportedOperationException('Stub only')
	}
}
