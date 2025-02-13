package com.test

import grails.plugin.springsecurity.annotation.Secured

@Secured('permitAll')
class LogoffController {

	static allowedMethods = [index: 'GET']

	def index() {}
}
