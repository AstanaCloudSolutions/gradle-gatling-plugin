package kz.cloudsolution.gatling.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Created by Alina on 28.03.16.
 */
class GatlingPlugin implements Plugin<Project> {

    final String GATLING_VERSION = '2.1.7'
    final String NIMBUS_VERSION = '4.12'

    private String gatlingReportsDirectory
    private Project project

    void apply(Project project) {
        this.project = project
        project.plugins.apply 'scala'
        project.extensions.create('gatling', GatlingPluginExtension)

        project.dependencies {
            testCompile "io.gatling.highcharts:gatling-charts-highcharts:$GATLING_VERSION",
                    "com.nimbusds:nimbus-jose-jwt:$NIMBUS_VERSION"
        }
        project.repositories {
            mavenCentral()
        }
        gatlingReportsDirectory = "$project.buildDir.absolutePath/gatling-reports"

        def executeScenario = { scenario, gatlingRequestBodiesDirectory, gatlingClasspath ->
            project.javaexec {
                main = 'io.gatling.app.Gatling'
                classpath = gatlingClasspath
                if (project.gatling.verbose) jvmArgs '-verbose'
                environment GATLING_HOME: ''
                args '-rf', gatlingReportsDirectory,
                        '-s', scenario,
                        '-bdf', gatlingRequestBodiesDirectory
                systemProperties(project.gatling.systemProperties ?: [:])
            }
        }

        project.task('scenarioTest',
                dependsOn: 'build') << {

            project.gatling.verifySettings()
            final def sourceSet = project.sourceSets.test
            final def gatlingRequestBodiesDirectory = firstPath(sourceSet.resources.srcDirs) + "/bodies"
            final def gatlingClasspath = sourceSet.output + sourceSet.runtimeClasspath
            final def scenarios = project.gatling._scenarios ?: gatlingScenarios(sourceSet)

            logger.lifecycle "Executing gatling scenarios: $scenarios"
            scenarios?.each { scenario ->
                executeScenario(scenario, gatlingRequestBodiesDirectory, gatlingClasspath)
            }
            logger.lifecycle "Gatling scenarios completed."
        }

        project.task('performanceTest',
                dependsOn: 'build') << {
            project.gatling.verifySettings()
            final def sourceSet = project.sourceSets.test
            final def gatlingRequestBodiesDirectory = firstPath(sourceSet.resources.srcDirs) + "/bodies"
            final def gatlingClasspath = sourceSet.output + sourceSet.runtimeClasspath
            final def scenarios = project.gatling._scenarios ?: gatlingPerformanceScenarios(sourceSet)

            logger.lifecycle "Executing gatling scenarios: $scenarios"
            scenarios?.each { scenario ->
                executeScenario(scenario, gatlingRequestBodiesDirectory, gatlingClasspath)
            }
            logger.lifecycle "Gatling scenarios completed."
        }
    }

    private gatlingPerformanceScenarios(sourceSet) {
        final String scenarioSrcDir = "$project.projectDir.absolutePath/src/$sourceSet.name/scala"
        final int scenarioPathPrefix = "$scenarioSrcDir/".size()
        final int scenarioPathSuffix = -('.scala'.size() + 1)
        sourceSet.allScala.files*.toString().
                findAll { it.endsWith 'Performance.scala' }.
                collect { it[scenarioPathPrefix..scenarioPathSuffix] }*.
                replace('/', '.')
    }

    private gatlingScenarios(sourceSet) {
        final String scenarioSrcDir = "$project.projectDir.absolutePath/src/$sourceSet.name/scala"
        final int scenarioPathPrefix = "$scenarioSrcDir/".size()
        final int scenarioPathSuffix = -('.scala'.size() + 1)
        sourceSet.allScala.files*.toString().
                findAll { it.endsWith 'Scenario.scala' }.
                collect { it[scenarioPathPrefix..scenarioPathSuffix] }*.
                replace('/', '.')
    }

    private firstPath(Set<File> files) {
        return files.toList().first().toString()
    }

    private openReport = { reportDir ->
        project.exec { commandLine 'open', "$reportDir/index.html" }
    }

    private withGatlingReportsDirs(Closure c) {
        new File(gatlingReportsDirectory).eachDirMatch(~/.*-\d+/, c)
    }

}

class GatlingPluginExtension {
    String scenario
    List scenarios
    boolean verbose

    Map systemProperties

    protected List get_scenarios() {
        scenarios ?: scenario ? [scenario] : null
    }

    protected void verifySettings() {
        if (scenario && scenarios) {
            throw new GatlingPluginConfigurationException('Should not define both gatling.scenario and gatling.scenarios')
        }
    }
}

class GatlingPluginConfigurationException extends Exception {
}