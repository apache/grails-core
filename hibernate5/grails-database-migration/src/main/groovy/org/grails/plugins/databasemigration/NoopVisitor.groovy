package org.grails.plugins.databasemigration

import liquibase.changelog.ChangeSet
import liquibase.changelog.DatabaseChangeLog
import liquibase.changelog.filter.ChangeSetFilterResult
import liquibase.changelog.visitor.ChangeSetVisitor
import liquibase.database.Database
import liquibase.exception.LiquibaseException

class NoopVisitor implements ChangeSetVisitor {

    protected Database database

    NoopVisitor(Database database) {
        this.database = database
    }

    Direction getDirection() { Direction.FORWARD }

    @Override
    void visit(ChangeSet changeSet, DatabaseChangeLog databaseChangeLog, Database database, Set<ChangeSetFilterResult> filterResults) throws LiquibaseException {
        changeSet.execute(databaseChangeLog, database)
    }
}
