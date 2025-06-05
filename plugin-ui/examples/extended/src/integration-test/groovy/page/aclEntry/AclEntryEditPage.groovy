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

package page.aclEntry

import geb.module.Checkbox
import geb.module.Select
import geb.module.TextInput
import page.EditPage

class AclEntryEditPage extends EditPage {

	static url = 'aclEntry/edit'
	static typeName = { 'AclEntry' }
	static content = {
		aclObjectIdentityId { $(name: 'aclObjectIdentity.id').module(TextInput) }
		aceOrder { $(name: 'aceOrder').module(TextInput) }
		mask { $(name: 'mask').module(TextInput) }
		sid { $(name: 'sid.id').module(Select) }
		auditFailure { $(name: 'auditFailure').module(Checkbox) }
		auditSuccess { $(name: 'auditSuccess').module(Checkbox) }
		granting { $(name: 'granting').module(Checkbox) }
	}
}
