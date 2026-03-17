import liquibase.harness.config.TestConfig
import liquibase.harness.diff.DiffCommandTestHelper

class HibernateDiffCommandTest extends DiffCommandTestHelper {
    static {
        TestConfig.instance.initDB = false
    }
}
