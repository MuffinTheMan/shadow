package com.github.jengelman.gradle.plugins.shadow

import com.github.jengelman.gradle.plugins.shadow.util.PluginSpecification
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Ignore
import spock.lang.IgnoreRest
import spock.lang.Issue

class FilteringSpec extends PluginSpecification {

    def setup() {
        repo.module('shadow', 'a', '1.0')
                .insertFile('a.properties', 'a')
                .insertFile('a2.properties', 'a2')
                .publish()
        repo.module('shadow', 'b', '1.0')
                .insertFile('b.properties', 'b')
                .publish()

        buildFile << """
            |apply plugin: 'com.github.johnrengelman.shadow'
            |apply plugin: 'java'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies {
            |   compile 'shadow:a:1.0'
            |   compile 'shadow:b:1.0'
            |}
            |
            |shadowJar {
            |   baseName = 'shadow'
            |   classifier = null
            |}
        """.stripMargin()

    }

    def 'include all dependencies'() {
        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties'])
    }

    def 'exclude files'() {
        given:
        buildFile << """
            |// tag::excludeFile[]
            |shadowJar {
            |   exclude 'a2.properties'
            |}
            |// end::excludeFile[]
        """.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'b.properties'])

        and:
        doesNotContain(output, ['a2.properties'])
    }

    def "exclude dependency"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |// tag::excludeDep[]
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:1.0'))
            |   }
            |}
            |// end::excludeDep[]
        '''.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])
    }

    @Issue('SHADOW-83')
    def "exclude dependency using wildcard syntax"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |// tag::excludeDepWildcard[]
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:.*'))
            |   }
            |}
            |// end::excludeDepWildcard[]
        '''.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])
    }

    @Issue("SHADOW-54")
    @Ignore("TODO - need to figure out the test pollution here")
    def "dependency exclusions affect UP-TO-DATE check"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])

        when: 'Update build file shadowJar dependency exclusion'
        buildFile.text = buildFile.text.replace('exclude(dependency(\'shadow:d:1.0\'))',
                                                'exclude(dependency(\'shadow:c:1.0\'))')

        BuildResult result = runner.withArguments('shadowJar').build()

        then:
        assert result.task(':shadowJar').outcome == TaskOutcome.SUCCESS

        and:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'd.properties'])

        and:
        doesNotContain(output, ['c.properties'])
    }

    @Issue("SHADOW-62")
    @Ignore
    def "project exclusions affect UP-TO-DATE check"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])

        when: 'Update build file shadowJar dependency exclusion'
        buildFile.text << '''
            |shadowJar {
            |   exclude 'a.properties'
            |}
        '''.stripMargin()

        BuildResult result = runner.withArguments('shadowJar').build()

        then:
        assert result.task(':shadowJar').outcome == TaskOutcome.SUCCESS

        and:
        contains(output, ['a2.properties', 'b.properties', 'd.properties'])

        and:
        doesNotContain(output, ['a.properties', 'c.properties'])
    }

    def "include dependency, excluding all others"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        file('src/main/java/shadow/Passed.java') << '''
            |package shadow;
            |public class Passed {}
        '''.stripMargin()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |       include(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['d.properties', 'shadow/Passed.class'])

        and:
        doesNotContain(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])
    }

    def 'filter project dependencies'() {
        given:
        buildFile.text = ''

        file('settings.gradle') << """
            |include 'client', 'server'
        """.stripMargin()

        file('client/src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
        """.stripMargin()

        file('client/build.gradle') << """
            |${defaultBuildScript}
            |apply plugin: 'java'
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
        """.stripMargin()

        file('server/src/main/java/server/Server.java') << """
            |package server;
            |import client.Client;
            |public class Server {}
        """.stripMargin()

        file('server/build.gradle') << """
            |${defaultBuildScript}
            |apply plugin: 'java'
            |apply plugin: 'com.github.johnrengelman.shadow'
            |
            |repositories { maven { url "${repo.uri}" } }
            |
            |// tag::excludeProject1[]
            |dependencies {
            |  compile project(':client')
            |}
            |
            |shadowJar {
            |// end::excludeProject1[]
            |   baseName = 'shadow'
            |   classifier = null
            |// tag::excludeProject2[]
            |   dependencies {
            |       exclude(project(':client'))
            |   }
            |}
            |// end::excludeProject2[]
        """.stripMargin()

        File serverOutput = file('server/build/libs/shadow.jar')

        when:
        runner.withArguments(':server:shadowJar').build()

        then:
        doesNotContain(serverOutput, [
                'client/Client.class',
        ])

        and:
        contains(serverOutput, ['server/Server.class', 'junit/framework/Test.class'])
    }

    def 'exclude a transitive project dependency'() {
        given:
        buildFile.text = ''

        file('settings.gradle') << """
            |include 'client', 'server'
        """.stripMargin()

        file('client/src/main/java/client/Client.java') << """
            |package client;
            |public class Client {}
        """.stripMargin()

        file('client/build.gradle') << """
            |${defaultBuildScript}
            |apply plugin: 'java'
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile 'junit:junit:3.8.2' }
        """.stripMargin()

        file('server/src/main/java/server/Server.java') << """
            |package server;
            |import client.Client;
            |public class Server {}
        """.stripMargin()

        file('server/build.gradle') << """
            |${defaultBuildScript}
            |apply plugin: 'java'
            |apply plugin: 'com.github.johnrengelman.shadow'
            |
            |repositories { maven { url "${repo.uri}" } }
            |dependencies { compile project(':client') }
            |
            |// tag::excludeSpec[]
            |shadowJar {
            |// end::excludeSpec[]
            |   baseName = 'shadow'
            |   classifier = null
            |// tag::excludeSpec2[]
            |   dependencies {
            |       exclude(dependency {
            |           it.moduleGroup == 'junit'
            |       })
            |   }
            |}
            |// end::excludeSpec2[]
        """.stripMargin()

        File serverOutput = file('server/build/libs/shadow.jar')

        when:
        runner.withArguments(':server:shadowJar').build()

        then:
        doesNotContain(serverOutput, [
                'junit/framework/Test.class'
        ])

        and:
        contains(serverOutput, [
                'client/Client.class',
                'server/Server.class'])
    }

    //http://mail-archives.apache.org/mod_mbox/ant-user/200506.mbox/%3C001d01c57756$6dc35da0$dc00a8c0@CTEGDOMAIN.COM%3E
    def 'verify exclude precedence over include'() {
        given:
        buildFile << """
            |// tag::excludeOverInclude[]
            |shadowJar {
            |   include '*.jar'
            |   include '*.properties'
            |   exclude 'a2.properties'
            |}
            |// end::excludeOverInclude[]
        """.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'b.properties'])

        and:
        doesNotContain(output, ['a2.properties'])
    }

    @Issue("SHADOW-69")
    def "handle exclude with circular dependency"() {
        given:
        repo.module('shadow', 'c', '1.0')
                .insertFile('c.properties', 'c')
                .dependsOn('d')
                .publish()
        repo.module('shadow', 'd', '1.0')
                .insertFile('d.properties', 'd')
                .dependsOn('c')
                .publish()

        buildFile << '''
            |dependencies {
            |   compile 'shadow:d:1.0'
            |}
            |
            |shadowJar {
            |   dependencies {
            |      exclude(dependency('shadow:d:1.0'))
            |   }
            |}
        '''.stripMargin()

        when:
        runner.withArguments('shadowJar').build()

        then:
        contains(output, ['a.properties', 'a2.properties', 'b.properties', 'c.properties'])

        and:
        doesNotContain(output, ['d.properties'])
    }

}