/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.plugins.databasemigration.liquibase

import liquibase.exception.CommandExecutionException
import org.grails.plugins.databasemigration.command.ApplicationContextDatabaseMigrationCommandSpec
import org.grails.plugins.databasemigration.command.DbmUpdateCommand

class GroovyPreconditionSpec extends ApplicationContextDatabaseMigrationCommandSpec {


    static List<String> executedChangeSets

    def setup() {
        executedChangeSets = []
    }
    def cleanup() {
        executedChangeSets.clear()
    }

    def "changeSet precondition is satisfied"() {
        given:
            def command = createCommand(DbmUpdateCommand)
            command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: 'John Smith', id: '1') {
        preConditions {
            grailsPrecondition {
                check {
                    assert true
                }
            }
        }
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
}
"""
        when:
            command.handle(getExecutionContext(DbmUpdateCommand))

        then:
            executedChangeSets == ['1']
    }

    def "changeSet precondition is not satisfied by using a simple assertion"() {
        given:
            def command = createCommand(DbmUpdateCommand)
            command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: 'John Smith', id: '1') {
        preConditions(onFail: 'CONTINUE') {
            grailsPrecondition {
                check {
                    assert false
                }
            }
        }
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
    changeSet(author: 'John Smith', id: '2') {
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
}
"""
        when:
            command.handle(getExecutionContext(DbmUpdateCommand))

        then:
            executedChangeSets == ['2']
    }

    def "changeSet precondition is not satisfied by using an assertion with a message"() {
        given:
            def command = createCommand(DbmUpdateCommand)
            command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: 'John Smith', id: '1') {
        preConditions(onFail: 'CONTINUE') {
            grailsPrecondition {
                check {
                    assert false: 'precondition is not satisfied'
                }
            }
        }
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
    changeSet(author: 'John Smith', id: '2') {
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
}
"""
        when:
            command.handle(getExecutionContext(DbmUpdateCommand))

        then:
            executedChangeSets == ['2']
    }

    def "changeSet precondition is not satisfied by calling the fail method"() {
        given:
            def command = createCommand(DbmUpdateCommand)
            command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: 'John Smith', id: '1') {
        preConditions(onFail: 'CONTINUE') {
            grailsPrecondition {
                check {
                    fail('precondition is not satisfied')
                }
            }
        }
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
    changeSet(author: 'John Smith', id: '2') {
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
}
"""
        when:
            command.handle(getExecutionContext(DbmUpdateCommand))

        then:
            executedChangeSets == ['2']
    }

    def "changeSet precondition is not satisfied by throwing an exception"() {
        given:
            def command = createCommand(DbmUpdateCommand)
            command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: 'John Smith', id: '1') {
        preConditions(onError: 'CONTINUE') {
            grailsPrecondition {
                check {
                    throw new RuntimeException('precondition is not satisfied')
                }
            }
        }
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
    changeSet(author: 'John Smith', id: '2') {
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
}
"""
        when:
            command.handle(getExecutionContext(DbmUpdateCommand))

        then:
            executedChangeSets == ['2']
    }

    def "databaseChangeLog precondition is not satisfied"() {
        given:
            def command = createCommand(DbmUpdateCommand)
            command.changeLogFile << """
databaseChangeLog = {
    preConditions {
        grailsPrecondition {
            check {
                assert false
            }
        }
    }
    changeSet(author: 'John Smith', id: '1') {
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
    changeSet(author: 'John Smith', id: '2') {
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
}
"""
        when:
            command.handle(getExecutionContext(DbmUpdateCommand))

        then:
            def e = thrown(CommandExecutionException)
            e.message.contains('1 preconditions failed')
            executedChangeSets == []
    }

    def "checks the available variables"() {
        given:
            def command = createCommand(DbmUpdateCommand)
            command.changeLogFile << """
databaseChangeLog = {
    changeSet(author: 'John Smith', id: '1') {
        preConditions(onError: 'CONTINUE') {
            grailsPrecondition {
                check {
                    assert database
                    assert databaseConnection
                    assert connection
                    assert sql
                    assert resourceAccessor
                    assert ctx
                    assert application
                    assert changeSet
                    assert changeLog
                }
            }
        }
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
    changeSet(author: 'John Smith', id: '2') {
        grailsChange {
            change {
                ${GroovyPreconditionSpec.name}.executedChangeSets << changeSet.id
            }
        }
    }
}
"""
        when:
            command.handle(getExecutionContext(DbmUpdateCommand))

        then:
            executedChangeSets == ['1','2']
    }
}
