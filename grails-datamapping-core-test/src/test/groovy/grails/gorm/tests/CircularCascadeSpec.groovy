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
package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.validation.PersistentEntityValidator
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.grails.datastore.gorm.validation.constraints.eval.DefaultConstraintEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.context.MessageSource
import spock.lang.Issue

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class CircularCascadeSpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([SchoolPerson, ActivityValidate, SportValidate, TeamValidate, ArenaValidate])
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/967')
    void "test circular cascade does not stackoverflow with persistent entity validator"() {
        given:
        SchoolPerson splinter = new SchoolPerson(name: 'Master Splinter')
        PersistentEntity entity = manager.session.datastore.getMappingContext().getPersistentEntity(SchoolPerson.name)
        def messageSource = Mock(MessageSource)
        messageSource.getMessage(_, _, _, _) >> 'test'
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(messageSource), manager.session.datastore.mappingContext, null)
        manager.session.datastore.getMappingContext().addEntityValidator(entity, new PersistentEntityValidator(entity, messageSource, evaluator))
        SchoolPerson leo = new SchoolPerson(name: 'Leonardo')
        SchoolPerson donnie = new SchoolPerson(name: 'Donatello')
        SchoolPerson mikey = new SchoolPerson(name: 'Michelangelo')
        SchoolPerson raph = new SchoolPerson(name: 'Raphael')

        splinter.addToStudents(leo)
        splinter.addToStudents(donnie)
        splinter.addToStudents(mikey)
        splinter.addToStudents(raph)

        leo.peers = [donnie, mikey, raph]
        donnie.peers = [leo, mikey, raph]
        mikey.peers = [leo, donnie, raph]
        raph.peers = [leo, donnie, mikey]

        expect:
        splinter.save(failOnError: true)
    }

    void addValidator(Class... classes) {
        classes.each { Class clazz ->
            PersistentEntity entity = manager.session.datastore.getMappingContext().getPersistentEntity(clazz.name)
            def messageSource = Mock(MessageSource)
            messageSource.getMessage(_, _, _, _) >> 'test'
            def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(messageSource), manager.session.datastore.mappingContext, null)
            manager.session.datastore.getMappingContext().addEntityValidator(entity, new PersistentEntityValidator(entity, messageSource, evaluator))
        }
    }

    @Issue('https://github.com/grails/grails-data-mapping/issues/1006')
    void "test multiple child associations are validated"() {
        given:
        addValidator(ActivityValidate, SportValidate, TeamValidate, ArenaValidate)
        ActivityValidate activity = new ActivityValidate(name: "Game")
        SportValidate sport = new SportValidate(name: 'Basketball')
        sport.addToTeams(new TeamValidate())
        sport.addToArenas(new ArenaValidate())
        activity.addToSports(sport)

        expect:
        !activity.validate()
        activity.errors.hasFieldErrors('sports[0].teams[0].name')
        activity.errors.hasFieldErrors('sports[0].arenas[0].name')
    }
}

@Entity
class SchoolPerson {
    String name

    static belongsTo = [master: SchoolPerson]

    static hasMany = [students: SchoolPerson, peers: SchoolPerson]
    static mappedBy = [students: 'none', peers: 'none']

    static constraints = {
        master nullable: true
    }
    static mapping = {
        peers cascade: 'none'
    }
}

@Entity
class ActivityValidate {
    String name

    static hasMany = [sports: SportValidate]
}

@Entity
class SportValidate {
    String name

    static hasMany = [teams: TeamValidate, arenas: ArenaValidate]
}

@Entity
class TeamValidate {
    String name
}

@Entity
class ArenaValidate {
    String name
}