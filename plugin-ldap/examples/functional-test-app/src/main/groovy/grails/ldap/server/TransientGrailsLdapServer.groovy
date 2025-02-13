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

import groovy.text.SimpleTemplateEngine

/**
 * This class was originally authored by Luke Daley from
 * https://github.com/ldaley/grails-ldap-server/blob/master/src/groovy/grails/ldap/server/TransientGrailsLdapServer.groovy
 * and licensed under the Apache License, Version 2.0:
 * https://github.com/ldaley/grails-ldap-server/blob/master/LICENSE
 */
@Slf4j
class TransientGrailsLdapServer implements InitializingBean, DisposableBean, BeanNameAware, GrailsApplicationAware {

	final static configOptions = ["port", "base", "indexed"]
	final static ldifFileNameFilter = [accept: { File dir, String name -> name.endsWith(".ldif") }] as FilenameFilter

	String beanName

	String baseWorkDir
	Integer port = 10389
	String base = "dc=grails,dc=org"
	String[] indexed = ["objectClass", "ou", "uid"]

	GrailsApplication grailsApplication
	DefaultDirectoryService directoryService
	LdapService ldapService

	LdapDN baseDn

	File configDir
	File dataDir
	File fixturesDir
	File schemaDir

	boolean running = false
	boolean initialised = false

	private def getLdapServerName() {
		return beanName - "LdapServer"
	}

	void afterPropertiesSet()
	{
		if (!initialised) {
			log.info("${beanName} config: " + configOptions.collect { "$it = ${this.properties[it]}" }.join(', '))

			def baseConfigDirPath = (grailsApplication.warDeployed) ? grailsApplication.parentContext.getResource("WEB-INF/grails-app/ldap-servers").file.path : "grails-app/ldap-servers"
			def baseConfigDir = new File(baseConfigDirPath)
			configDir = new File(baseConfigDir, ldapServerName)
			dataDir = new File(configDir, "data")
			fixturesDir = new File(configDir, "fixtures")
			schemaDir = new File(configDir, "schema")
			baseDn = new LdapDN(base)

			start()
			initialised = true

			addShutdownHook {
				this.stop()
			}
		}
	}

	void start() {
		if (!running) {
			log.info("${beanName} starting")
			startDirectoryService()

			loadLdif(schemaDir)
			loadLdif(dataDir)

			directoryService.changeLog.tag()

			startLdapService()
			running = true
			log.info("${beanName} starup complete")
		}
	}

	void stop() {
		if (running) {
			log.info("${beanName} stopping")
			stopDirectoryService()
			stopLdapService()
			running = false
			log.info("${beanName} stopped")
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
			log.info("${beanName} cleaning")
			directoryService.revert()
			directoryService.changeLog.tag()
		}
	}

	void loadFixture(String fixtureName) {
		loadFixtures(fixtureName)
	}

	void loadFixture(Map binding, String fixtureName) {
		loadFixtures([fixtureName] as String[], binding)
	}

	void loadFixture(String fixtureName, Map binding) {
		loadFixtures([fixtureName] as String[], binding)
	}

	void loadFixtures(String[] fixtureNames) {
		loadFixtures(fixtureNames, [:])
	}

	void loadFixtures(Map binding, String[] fixtureNames) {
		loadFixtures(fixtureNames, binding)
	}

	void loadFixtures(String[] fixtureNames, Map binding) {
		binding = binding ?: [:]
		fixtureNames.each { fixtureName ->
			def fixture = new File(fixturesDir, "${fixtureName}.ldif")
			if (fixture.exists()) {
				log.debug("${beanName}: loading fixture ${fixtureName}, binding = ${binding}")
				def fixtureReader = new FileReader(fixture)
				def engine = new SimpleTemplateEngine()
				def ldif = engine.createTemplate(fixtureReader).make(binding).toString()
				loadLdif(ldif)
			} else {
				throw new IllegalArgumentException("Cannot load fixture '${fixtureName} as it does not exist")
			}
		}
	}

	void loadLdif(String ldif) {
		log.debug("${beanName}: loading ldif '$ldif'")
		consumeLdifReader(new LdifReader(new StringReader(ldif)))
	}

	void loadLdif(File file) {
		if (file.exists()) {
			if (file.directory) {
				log.debug("Loading ldif in dir: ${file}")
				file.listFiles(ldifFileNameFilter).sort().each {
					loadLdif(it)
				}
			} else {
				log.debug("Loading ldif in file: ${file}")
				consumeLdifReader(new LdifReader(file))
			}
		}
	}

	void loadLdif(ldif) {
		loadLdif(ldif as String)
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
		} catch (LdapNameNotFoundException e) {
			null
		}
	}

	private startDirectoryService() {

		directoryService = new DefaultDirectoryService()
		directoryService.changeLog.enabled = true
		def workingDir = getWorkDir()
		if (workingDir.exists()) workingDir.deleteDir()
		directoryService.workingDirectory = workingDir

		def partition = addPartition(baseDn.rdn.normValue, base)
		addIndex(partition, *indexed)

		directoryService.startup()
		createBase()
	}

	private startLdapService() {
		ldapService = new LdapService()
		ldapService.socketAcceptor = new SocketAcceptor(null)
		ldapService.directoryService = directoryService
		ldapService.ipPort = port

		ldapService.start()
	}

	private stopDirectoryService() {
		directoryService.shutdown()
	}

	private stopLdapService() {
		ldapService.stop()
	}

	private createBase() {
		def entry = directoryService.newEntry(baseDn)
		entry.add("objectClass", "top", "domain", "extensibleObject")
		entry.add(baseDn.rdn.normType, baseDn.rdn.normValue)
		directoryService.adminSession.add(entry)
	}

	private addPartition(partitionId, partitionDn) {
		def partition = new JdbmPartition()
		partition.id = partitionId
		partition.suffix = partitionDn
		directoryService.addPartition(partition)

		partition
	}

	private addIndex(partition, String[] attrs) {
		partition.indexedAttributes = attrs.collect { new JdbmIndex(it) } as Set
	}

	private consumeLdifReader(ldifReader) {
		while (ldifReader.hasNext()) {
			LdifEntry entry = ldifReader.next()
			if ( entry.isChangeModify() ) {
				directoryService.adminSession.modify(entry.dn, entry.modificationList)
			} else {
				def ldif = LdifUtils.convertToLdif(entry, Integer.MAX_VALUE)
				directoryService.adminSession.add(directoryService.newEntry(ldif, entry.dn.toString()))
			}
		}
	}

	private getWorkDir() {
		def base = baseWorkDir ?: "."
		new File(base, "ldap-servers/$ldapServerName")
	}
}
