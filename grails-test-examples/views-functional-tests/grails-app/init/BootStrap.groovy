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

import functional.tests.Bus
import functional.tests.Car
import functional.tests.Circular
import functional.tests.Customer
import functional.tests.Employee
import functional.tests.Garage
import functional.tests.MyEnum
import functional.tests.Player
import functional.tests.Project
import functional.tests.Site
import functional.tests.Team
import functional.tests.Proxy
import grails.gorm.transactions.Transactional
import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {

    def init = {
        setupData()
    }

    @Transactional
    void setupData() {
        Team t = new Team(name: "Barcelona")

        def captain = new Player(name: "Iniesta")
        t.captain = captain
        t.players = [captain, new Player(name:"Messi")]
        t.save(flush:true)

        Circular c = new Circular(name: "topLevel", myEnum: MyEnum.BAR)
        c.addToCirculars([name: "topLevel-2", myEnum: MyEnum.FOO])
        c.addToCirculars([name: "topLevel-3", myEnum: MyEnum.BAR])
        c.save(flush: true, failOnError: true)

        new Proxy(name: "Sally").save(flush: true, failOnError: true)

        new Garage(owner: "Jay Leno")
                .addToVehicles(new Bus(maxPassengers: 30, route: "around town"))
                .addToVehicles(new Car(maxPassengers: 4, make: "Subaru", model: "WRX", year: 2016))
                .save(flush: true, failOnError: true)

        new Customer(name: "Nokia")
                .addToSites(new Site(name: "Salo"))
                .addToSites(new Site(name: "Helsinki"))
                .save(flush: true, failOnError: true)

        new Project(name: "Grails Views")
                .addToEmployees(new Employee(name: "James Kleeh"))
                .addToEmployees(new Employee(name: "Iván López"))
                .save(flush: true, failOnError: true)
    }
}
