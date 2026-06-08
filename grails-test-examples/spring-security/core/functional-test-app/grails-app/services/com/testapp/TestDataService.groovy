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

package com.testapp

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.gorm.transactions.Transactional
import groovy.sql.Sql
import rest.Book
import rest.Customer
import rest.Movie
import rest.Stuff
import rest.Thing

@Transactional
class TestDataService {

	def dataSource
	def objectDefinitionSource

	//	'/error', '/hack/**', and '/testData/**' are handled in TestRequestmapFilterInvocationDefinition
	static final List<String> URIS_FOR_REQUESTMAPS = [
		'/', '/**/css/**', '/**/favicon.ico', '/**/images/**', '/**/js/**', '/assets/**', '/dbconsole',
		'/dbconsole/**', '/index', '/index.gsp', '/login', '/login/**', '/logoff', '/shutdown', '/misctest/**',
		'/testrequestmap', '/testrequestmap/**', '/testrole', '/testrole/**', '/testuser', '/testuser/**']

	void returnToInitialState() {
		truncateDomainClasses()
		//truncateTablesAndRetry 3, false
		enterInitialData()
		objectDefinitionSource.reset()
	}


	boolean truncateDomainClasses() {
		TestUser.withNewTransaction {
			TestUserTestRole.where {}.deleteAll()
			TestRequestmap.where {}.deleteAll()
			TestRole.where {}.deleteAll()
			TestUser.where {}.deleteAll()
			Book.where {}.deleteAll()
			Customer.where {}.deleteAll()
			Movie.where {}.deleteAll()
			Stuff.where {}.deleteAll()
			Thing.where {}.deleteAll()
		}
	}

	boolean truncateTablesAndRetry(int retryCount, boolean ignoreExceptions) {
		for (int i = 0; i < retryCount; i++) {
			// foreign key constraints cause exceptions when deleting data in wrong order
			// just ignore them and re-try 3 times
			if (truncateTables(true)) {
				break
			}
		}
		truncateTables ignoreExceptions // make sure everything is deleted
	}

	boolean truncateTables(boolean ignoreExceptions = false) {
		println "truncateTables: start"
		Sql sql
		boolean success = true
		try {
			sql = new Sql(dataSource)
			sql.rows('SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = SCHEMA()').each { row ->
				try {
					int rowCount = sql.executeUpdate('DELETE FROM ' + row.TABLE_NAME)
					if (rowCount) println "truncateTables: deleted $rowCount row(s) from $row.TABLE_NAME"
				}
				catch (e) {
					success = false
					if (!ignoreExceptions) {
						throw e
					}
				}
			}
		}
		finally {
			println "truncateTables: end"
			sql?.close()
		}
		success
	}

	void enterInitialData() {
		if ( !Book.findByTitle('TestBook') ) {
			new Book(title: 'TestBook').save(flush: true)
		}
		if ( !Movie.findByTitle('TestMovie') ) {
			new Movie(title: 'TestMovie').save(flush: true)
		}

		if (System.getProperty('add_test_users')) {
			addTestUsers()
		}

		if (SpringSecurityUtils.securityConfigType != 'Requestmap') {
			return
		}

		if (TestRequestmap.count()) {
			return
		}

		for (url in URIS_FOR_REQUESTMAPS) {
			save new TestRequestmap(url: url, configAttribute: 'permitAll'), true
		}

		assert URIS_FOR_REQUESTMAPS.size() == TestRequestmap.count()
	}

	void addTestUsers() {
		println 'Adding test users'
		addTestUser 'admin',                     'ROLE_ADMIN'
		addTestUser 'testuser',                  'ROLE_USER', 'ROLE_BASE', 'ROLE_EXTENDED'
		addTestUser 'testuser_books',            'ROLE_BOOKS'
		addTestUser 'testuser_movies',           'ROLE_MOVIES'
		addTestUser 'testuser_books_and_movies', 'ROLE_BOOKS', 'ROLE_MOVIES'
	}

	TestUser addTestUser(String username, String... roles) {
		def testUser = save(new TestUser(username: username, password: 'password'), true)
		roles.each { save new TestUserTestRole(testUser: testUser, testRole: TestRole.findOrSaveByAuthority(it)), true }
		testUser
	}

	private save(o, boolean flush = false) {
		o.save(failOnError: true, flush: flush)
	}
}
