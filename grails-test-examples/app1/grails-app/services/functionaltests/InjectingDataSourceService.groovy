package functionaltests

import groovy.transform.CompileStatic

@CompileStatic
class InjectingDataSourceService {

    def dataSource

    boolean dataSourceInjectionWorked() {
        dataSource != null
    }
}