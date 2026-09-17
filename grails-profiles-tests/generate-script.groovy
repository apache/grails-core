@Grab('org.yaml:snakeyaml:1.23')

File f = new File('travis-build.sh')

f << '#!/bin/bash\n' +
        'set -e\n' +
        '\n' +
        'EXIT_STATUS=0\n\n'

import org.yaml.snakeyaml.Yaml
List commands = new Yaml().load((".travis.yml" as File).text).script

List<String> skipCommands = [
        'curl -s get.sdkman.io | bash',
        'source "$HOME/.sdkman/bin/sdkman-init.sh"',
        'echo sdkman_auto_answer=true > ~/.sdkman/etc/config',
        'source "/home/travis/.sdkman/bin/sdkman-init.sh"',
        './gradlew build --console=plain',
        'cd build/grails-wrapper/',
        './gradlew assemble',
        'cd ../../',
        'mkdir -p $HOME/.grails/wrapper',
        'cp /home/travis/build/grails/grails-profiles-tests/build/grails-wrapper/wrapper/build/libs/grails4-wrapper-1.0.1.BUILD-SNAPSHOT.jar $HOME/.grails/wrapper/grails4-wrapper.jar',
        'sdk install grails dev /home/travis/build/grails/grails-profiles-tests/build/grails-core',
        'sdk install grails',
        'sdk use grails dev',
        'grails --version',
]
commands -= skipCommands
commands.each {
    f << "${it} || EXIT_STATUS=\$? \n"
    f << '\nif [ $EXIT_STATUS -ne 0 ]; then\n'
    f << '  exit $EXIT_STATUS\n'
    f << 'fi\n\n\n'
}