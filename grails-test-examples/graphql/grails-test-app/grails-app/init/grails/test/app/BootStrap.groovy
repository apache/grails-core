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

package grails.test.app

import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {

    DogService dogService
    LabradoodleService labradoodleService
    HumanService humanService
    GrailsTeamMemberService grailsTeamMemberService

    def init = { servletContext ->

        dogService.save("Spot", 60)
        labradoodleService.save("Chloe", 60)
        humanService.save("Kotlin Ken")

        grailsTeamMemberService.save("Nero")
        grailsTeamMemberService.save("Colin")
        grailsTeamMemberService.save("Graeme")
        grailsTeamMemberService.save("Jack")
        grailsTeamMemberService.save("James")
        grailsTeamMemberService.save("Ryan")
        grailsTeamMemberService.save("Matthew")
        grailsTeamMemberService.save("Will")
        grailsTeamMemberService.save("Alvaro")
        grailsTeamMemberService.save("Dave")
        grailsTeamMemberService.save("Ivan")
        grailsTeamMemberService.save("Jeff")
        grailsTeamMemberService.save("Paul")
        grailsTeamMemberService.save("Ben")
        grailsTeamMemberService.save("Sergio")
        grailsTeamMemberService.save("Zack")
    }
    def destroy = {
    }
}
