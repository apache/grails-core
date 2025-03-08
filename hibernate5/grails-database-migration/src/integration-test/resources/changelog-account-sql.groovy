databaseChangeLog = {
    changeSet(id: 'create-account-sql', author: 'integration-test') {
        sql "INSERT INTO account (version, name) VALUES (0, 'Joseph');"
    }
}