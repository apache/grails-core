databaseChangeLog = {
    changeSet(id: "create-person-table", author: 'integration-test') {
        createTable(tableName: "person") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(primaryKey: "true")
            }

            column(name: "version", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "first_name", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }

            column(name: "age", type: "INT") {
                constraints(nullable: "false")
            }

            column(name: "gender", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }

            column(name: "last_name", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }

            column(name: "cell", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }

            column(name: "email_address", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }
        }
    }

    changeSet(id: "create-account-table", author: 'integration-test') {
        createTable(tableName: "account") {
            column(autoIncrement: "true", name: "id", type: "BIGINT") {
                constraints(primaryKey: "true", primaryKeyName: "accountPK")
            }

            column(name: "version", type: "BIGINT") {
                constraints(nullable: "false")
            }

            column(name: "name", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }

            column(name: "number", type: "VARCHAR(255)") {
                constraints(nullable: "false")
            }
        }
    }
}