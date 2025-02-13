package grails.plugin.springsecurity.ldap.userdetails

import org.springframework.ldap.core.support.LdapContextSource
import org.springframework.security.core.authority.SimpleGrantedAuthority

import spock.lang.Specification

class GrailsLdapAuthoritiesPopulatorSpec extends Specification {

	private static final String[] rolesToTest = [
		'EnHS-GLNE',
		'EnHS-NCS-Financial',
		'EnHS-WBFCR',
		'EnHS-HS-AssetTrackers',
		'EnHS-IT',
		'EnHS-NCS-All',
		'EnHS-NCS-Remove-List-Member',
		'EnHS-NCS-List-Viewer',
		'EnHS-NCS-Stress',
		'EnHS-HS-Lyris',
		'EnHS-Staff',
		'NCS-Protected-Stress',
		'EnHS-NCS-DocGen-Manage',
		'EnHS-NunStudy-Investigator',
		'EnHS-NCS-Segment',
		'EnHS-NCS',
		'EnHS-NCS-Protected-Exec-Function',
		'EnHS-MES',
		'EnHS-NCS-Edit-List-Member',
		'EnHS-NCS-Lookup',
		'EnHS-NCS-Protected-Ulnar-Length',
		'EnHS-NCS-Receipt',
		'NCS-HumanResources',
		'EnHS-NCS-Add-List-Member',
		'EnHS-HS-DBAs',
		'EnHS-NunStudy-Administrative',
		'EnHS-NCS-Calling',
		'EnHS-NCS-Exec-Function',
		'EnHS-NunStudy',
		'EnHS-NCS-Segment-Lookup',
		'EnHS-NCS-Security',
		'EnHS-Print-Admins',
		'EnHS-NCS-Formative',
		'EnHS-MTC-RemoteDesktopUsers',
		'NCS-Ulnar-Length',
		'EnHS-HS-All',
		'EnHS-AVAD',
		'EnHS-NCS-DLR',
		'EnHS-NCS-Protected',
		'EnHS-NCS-Incentives',
		'EnHS-NCST',
		'EnHS-HS-IT',
		'EnHS-NCS-Nutrition',
		'EnHS-HPVHSS',
		'EnHS-NCS-Assign-List-Auth',
		'EnHS-HS-Staff',
		'EnHS-NCS-DocGen',
		'EnHS-NCS-List-Admin',
		'EnHS-NCS-List-Tester',
		'EnHS-NCS-Protected-Nutrition',
		'EnHS-NCS-NORC-Data',
		'EnHS-NCS-Data',
		'EnHS-HS-BulkPrinters',
		'NCS-IT',
		'EnHS-All',
		'EnHS-NCS-Reports']

	private LdapContextSource contextSource = new LdapContextSource()

	private GrailsLdapAuthoritiesPopulator grailsLdapAuthoritiesPopulator = new GrailsLdapAuthoritiesPopulator(contextSource, '')

	void setup() {
		grailsLdapAuthoritiesPopulator.groupRoleAttribute = 'member'
		grailsLdapAuthoritiesPopulator.groupSearchFilter = 'fake={0}'
		grailsLdapAuthoritiesPopulator.searchSubtree = true
		grailsLdapAuthoritiesPopulator.defaultRole = 'ROLE_USER'
		grailsLdapAuthoritiesPopulator.ignorePartialResultException = false
		grailsLdapAuthoritiesPopulator.retrieveDatabaseRoles = false
		grailsLdapAuthoritiesPopulator.roleConvertDashes = true
		grailsLdapAuthoritiesPopulator.roleToUpperCase = true
	}

	/**
	 * This one test should cover everything added in the cleanRole() function
	 */
	void 'my roles'() {

		given:
		grailsLdapAuthoritiesPopulator.roleStripPrefix = 'EnHS-'

		when:

		def testRole = new SimpleGrantedAuthority('ROLE_' + roleName)

		// the settings should run through all the permutations in one swipe
		def newRole = grailsLdapAuthoritiesPopulator.cleanRole(testRole)

		String cleanRoleName = 'ROLE_' + roleName.toUpperCase().replaceAll('-', '_').replaceFirst('ENHS_', '')

		then:
		// make sure our test did what we expected it to
		cleanRoleName == newRole.authority

		where:
		roleName << rolesToTest
	}

	/**
	 * This one test should cover everything added in the cleanRole() function
	 */
	void 'get group membership roles'() {

		given:
		grailsLdapAuthoritiesPopulator.roleStripPrefix = 'Test-Pre'
		grailsLdapAuthoritiesPopulator.roleStripSuffix = 'Test-Post'

		when:
		def testRole = new SimpleGrantedAuthority('ROLE_Test-Pre Sys-AdminTest-Pre-Test-Post-Group Test-Post')

		// the settings should run through all the permutations in one swipe
		def newRole = grailsLdapAuthoritiesPopulator.cleanRole(testRole)

		then:
		// make sure our test did what we expected it to
		'ROLE_SYS_ADMINTEST_PRE_TEST_POST_GROUP' == newRole.authority
	}
}
