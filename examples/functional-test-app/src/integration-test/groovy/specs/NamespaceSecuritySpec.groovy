package specs

import grails.testing.mixin.integration.Integration
import groovy.json.JsonSlurper
import pages.IndexPage
import pages.LoginPage
import spock.lang.IgnoreIf
import spock.lang.Unroll

@Integration
@IgnoreIf({ System.getProperty('TESTCONFIG') != 'annotation' })
class NamespaceSecuritySpec extends AbstractSecuritySpec {

	protected void resetDatabase() {
		super.resetDatabase()
		go 'testData/addTestUsers'
	}

    @Unroll
	void '#path should redirect to login page for anonymous'(String uri, String path) {
		when:
		go path

		then:
		at LoginPage

		where:
		uri << ['books', 'books.json', 'movies', 'movies.json']
        path = 'api/v1/' + uri
	}

	void 'api not allowed for testuser'() {
		when:
		login 'testuser', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'api/v1/movies' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		where:
		format << ['', '.json']
	}

	void 'verify security for testuser_books'() {
		when:
		login 'testuser_books', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		jsonResultTitle == 'TestBook'

		when:
		go 'api/v1/movies' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		where:
		format << ['', '.json']
	}

	void 'verify security for testuser_movies'() {
		when:
		login 'testuser_movies', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		$('.errors').text() == "Sorry, you're not authorized to view this page."

		when:
		go 'api/v1/movies' + format

		then:
		jsonResultTitle == 'TestMovie'

		where:
		format << ['', '.json']
	}

	void 'verify security for testuser_books_and_movies'() {
		when:
		login 'testuser_books_and_movies', 'password'

		then:
		at IndexPage

		when:
		go 'api/v1/books' + format

		then:
		jsonResultTitle == 'TestBook'

		when:
		go 'api/v1/movies' + format

		then:
		jsonResultTitle == 'TestMovie'

		where:
		format << ['', '.json']
	}

	void 'namespaced controller with same name can have different secured annotations - open'() {
		when:
		go 'openNamespaced'

		then:
		pageSource.contains 'open'
	}

	void 'namespaced controller with same name can have different secured annotations - secured'() {
		when:
		go 'secureNamespaced'

		then:
		at LoginPage
	}

	private String getJsonResultTitle() {

		def matcher = pageSource =~ /.*(\[\{.+\}\]).*/
		assert matcher.hasGroup()
		assert matcher.count == 1

		def results = new JsonSlurper().parseText(matcher[0][1])
		assert results instanceof List
		assert results.size() == 1
		assert results[0].id instanceof Number

		results[0].title
	}
}
