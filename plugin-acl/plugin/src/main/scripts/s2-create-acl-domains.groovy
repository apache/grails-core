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

/**
 * Copies the plugin's ACL domain classes to the project. The package and names cannot be
 * specified since plugin classes depend on them. But they should be in the project's
 * grails-app/domain folder to allow customization such as Hibernate 2nd-level caching.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */

description 'Copies ACL domain classes to the project', {
	usage 'grails s2-create-acl-domains'
}

['AclClass', 'AclEntry', 'AclObjectIdentity', 'AclSid'].each { String name ->
	render template: template('_' + name + '.groovy'),
	       destination: file("grails-app/domain/grails/plugin/springsecurity/acl/${name}.groovy"),
	       overwrite: false
}
