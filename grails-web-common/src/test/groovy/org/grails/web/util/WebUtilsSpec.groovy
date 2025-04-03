package org.grails.web.util

import spock.lang.Issue
import spock.lang.Specification

/**
 * @Author Sudhir Nimavat
 */
class WebUtilsSpec extends Specification {

	@Issue("https://github.com/apache/grails-core/issues/10545")
	def testToQueryString() {
		given:
		Map params = ["name":"sudhir-nimavat", "address.zip":"12345"]

		when:
		String result = WebUtils.toQueryString(params)

		then:
		result.startsWith("?")
		def tokens = result[1..-1].split('&')
		tokens.find({ it == "name=sudhir-nimavat"}) != null
		tokens.find({ it == "address.zip=12345"}) != null
	}
}
