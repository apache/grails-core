package bean.injection

import io.micronaut.context.annotation.Primary

import jakarta.inject.Singleton

@Singleton
@Primary
class PrimaryNamedService implements NamedService {

    final String name = 'primary'

}

