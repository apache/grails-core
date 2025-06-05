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

package grails.ldap.server

import grails.core.support.GrailsApplicationAware
import groovy.util.logging.Slf4j

import org.apache.directory.server.core.DefaultDirectoryService
import org.apache.directory.server.ldap.LdapService
import org.apache.directory.server.protocol.shared.SocketAcceptor
import org.apache.directory.shared.ldap.name.LdapDN
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex
import org.apache.directory.shared.ldap.ldif.LdifEntry
import org.apache.directory.shared.ldap.ldif.LdifReader
import org.apache.directory.shared.ldap.ldif.LdifUtils
import org.apache.directory.shared.ldap.exception.LdapNameNotFoundException

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.BeanNameAware

import grails.core.GrailsApplication

/**
 * This class was originally authored by Luke Daley from
 * https://github.com/ldaley/grails-ldap-server/blob/master/src/groovy/grails/ldap/server/TransientGrailsLdapServer.groovy
 * and licensed under the Apache License, Version 2.0:
 * https://github.com/ldaley/grails-ldap-server/blob/master/LICENSE
 */
@Slf4j
class TransientGrailsLdapServer implements InitializingBean, DisposableBean, BeanNameAware, GrailsApplicationAware {

	final static configOptions = ['port', 'base', 'indexed']
	final static ldifFileNameFilter = [accept: { File dir, String name -> name.endsWith('.ldif') }] as FilenameFilter

	String beanName

	Integer port = 10389
	String base = 'dc=grails,dc=org'
	String[] indexed = ['objectClass', 'ou', 'uid']

	GrailsApplication grailsApplication
	DefaultDirectoryService directoryService
	LdapService ldapService

	LdapDN baseDn

	File configDir
	File dataDir
	File schemaDir

	boolean running = false
	boolean initialised = false

	private def getLdapServerName() {
		return beanName - 'LdapServer'
	}

	void afterPropertiesSet() {
		if (!initialised) {
			log.info("${beanName} config: " + configOptions.collect { "$it = ${this.properties[it]}" }.join(', '))
			def baseConfigDir = createLdapTestConfigDir()
			configDir = new File(baseConfigDir, ldapServerName)
			dataDir = new File(configDir, 'data')
			schemaDir = new File(configDir, 'schema')
			baseDn = new LdapDN(base)

			start()
			initialised = true

			addShutdownHook {
				this.stop()
				baseConfigDir.deleteDir()
			}
		}
	}

	void start() {
		if (!running) {
			log.info('{} starting', beanName)
			startDirectoryService()

			loadLdif(schemaDir)
			loadLdif(dataDir)

			directoryService.changeLog.tag()

			startLdapService()
			running = true
			log.info('{} startup complete', beanName)
		}
	}

	void stop() {
		if (running) {
			log.info('{} stopping', beanName)
			stopDirectoryService()
			stopLdapService()
			running = false
			log.info('{} stopped', beanName)
		}
	}

	void destroy() {
		stop()
	}

	void restart() {
		stop()
		start()
	}

	void clean() {
		if (running) {
			log.info('{} cleaning', beanName)
			directoryService.revert()
			directoryService.changeLog.tag()
		}
	}

	private void loadLdif(File file) {
		if (file.exists()) {
			if (file.directory) {
				log.debug('Loading ldif in dir: {}', file)
				file.listFiles(ldifFileNameFilter).sort().each {
					loadLdif(it)
				}
			} else {
				log.debug('Loading ldif in file: {}', file)
				consumeLdifReader(new LdifReader(file))
			}
		}
	}

	boolean exists(String dn) {
		directoryService.adminSession.exists(new LdapDN(dn as String))
	}

	Map getAt(String dn) {
		try {
			def entry = directoryService.adminSession.lookup(new LdapDN(dn))
			def entryMap = [:]
			entry.attributeTypes.each { at ->
				def attribute = entry.get(at)
				if (at.singleValue) {
					entryMap[attribute.id] = (attribute.isHR()) ? attribute.string : attribute.bytes
				} else {
					def values = []
					attribute.all.each {
						values << it.get()
					}
					entryMap[attribute.id] = values
				}
			}
			entryMap
		} catch (LdapNameNotFoundException ignored) {
			null
		}
	}

	private void startDirectoryService() {
		directoryService = new DefaultDirectoryService()
		directoryService.changeLog.enabled = true

		def workingDir = new File(File.createTempDir('ldap-servers', 'working'), ldapServerName)
		workingDir.mkdirs()
		directoryService.workingDirectory = workingDir

		def partition = addPartition(baseDn.rdn.normValue, base)
		addIndex(partition, indexed)

		directoryService.startup()
		createBase()
	}

	private void startLdapService() {
		ldapService = new LdapService()
		ldapService.socketAcceptor = new SocketAcceptor(null)
		ldapService.directoryService = directoryService
		ldapService.ipPort = port
		ldapService.start()
	}

	private void stopDirectoryService() {
		directoryService.shutdown()
		directoryService.workingDirectory.parentFile.deleteDir()
	}

	private void stopLdapService() {
		ldapService.stop()
	}

	private void createBase() {
		def entry = directoryService.newEntry(baseDn)
		entry.add('objectClass', 'top', 'domain', 'extensibleObject')
		entry.add(baseDn.rdn.normType, baseDn.rdn.normValue)
		directoryService.adminSession.add(entry)
	}

	private JdbmPartition addPartition(partitionId, partitionDn) {
		def partition = new JdbmPartition()
		partition.id = partitionId
		partition.suffix = partitionDn
		directoryService.addPartition(partition)
		return partition
	}

	private static void addIndex(partition, String[] attrs) {
		partition.indexedAttributes = attrs.collect { new JdbmIndex(it) } as Set
	}

	private void consumeLdifReader(ldifReader) {
		while (ldifReader.hasNext()) {
			LdifEntry entry = ldifReader.next()
			if (entry.isChangeModify()) {
				directoryService.adminSession.modify(entry.dn, entry.modificationItems)
			} else {
				def ldif = LdifUtils.convertToLdif(entry, Integer.MAX_VALUE)
				directoryService.adminSession.add(directoryService.newEntry(ldif, entry.dn.toString()))
			}
		}
	}

	private static File createLdapTestConfigDir() {
		def ldapData = '''\
			dn: ou=groups,dc=d1,dc=example,dc=com
			objectclass: organizationalUnit
			objectclass: top
			ou: groups
			
			dn: cn=USER,ou=groups,dc=d1,dc=example,dc=com
			objectclass: groupOfUniqueNames
			cn: USER
			objectclass: top
			uniqueMember: cn=person1,dc=d1,dc=example,dc=com
			uniqueMember: cn=person2,dc=d1,dc=example,dc=com
			uniqueMember: cn=person3,dc=d1,dc=example,dc=com
			
			dn: cn=ADMIN,ou=groups,dc=d1,dc=example,dc=com
			objectclass: groupOfUniqueNames
			objectclass: top
			cn: ADMIN
			uniqueMember: cn=person2,dc=d1,dc=example,dc=com
			
			dn: cn=foo bar,ou=groups,dc=d1,dc=example,dc=com
			objectclass: groupOfUniqueNames
			objectclass: top
			cn: foo bar
			uniqueMember: cn=person1,dc=d1,dc=example,dc=com
			
			dn: cn=person1,dc=d1,dc=example,dc=com
			objectClass: uidObject
			objectClass: person
			objectClass: top
			objectClass: organizationalPerson
			uid: person1
			userPassword: {SHA}44rSFJQ9qtHWTBAvrsKd5K/p2j0=
			cn: person1
			sn: jones
			
			dn: cn=person2,dc=d1,dc=example,dc=com
			objectClass: uidObject
			objectClass: person
			objectClass: top
			objectClass: organizationalPerson
			uid: person2
			userPassword: {SHA}KqYKj/f81HPTIeAUav2eJt85UUc=
			cn: person2
			sn: jones
			
			dn: cn=person3,dc=d1,dc=example,dc=com
			objectClass: uidObject
			objectClass: person
			objectClass: top
			objectClass: organizationalPerson
			uid: person3
			userPassword: {SHA}ERnP037iRzV+A0oI2ETuol9v0g8=
			cn: person3
			sn: jones
			'''.stripIndent(3)
		def baseConfigDir = File.createTempDir('ldap-servers')
		def d1DataDir = new File(new File(baseConfigDir, 'd1'), 'data')
		d1DataDir.mkdirs()
		def d1UsersLdif = new File(d1DataDir, 'users.ldif')
		d1UsersLdif.text = ldapData
		return baseConfigDir
	}
}
