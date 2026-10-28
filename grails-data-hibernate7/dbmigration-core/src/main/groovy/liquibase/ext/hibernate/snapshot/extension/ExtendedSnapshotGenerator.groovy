package liquibase.ext.hibernate.snapshot.extension

import groovy.transform.CompileStatic

@CompileStatic
interface ExtendedSnapshotGenerator<T, U> {

    U snapshot(T object)

    boolean supports(T object)

}
