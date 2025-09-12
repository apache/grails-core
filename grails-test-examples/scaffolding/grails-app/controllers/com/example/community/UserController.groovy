package com.example.community

import grails.plugin.scaffolding.RestfulServiceController
import grails.plugin.scaffolding.annotation.Scaffold

@Scaffold(RestfulServiceController<User>)
class UserController {
    static namespace = 'community'
}

