/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.plugins.quality

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.quality.internal.CodeNarcReportsImpl
import org.gradle.api.reporting.Report
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.*
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.reflect.Instantiator
import org.gradle.logging.ConsoleRenderer

import javax.inject.Inject

/**
 * Runs CodeNarc against some source files.
 */
class CodeNarc extends SourceTask implements VerificationTask, Reporting<CodeNarcReports> {
    /**
     * The class path containing the CodeNarc library to be used.
     */
    @InputFiles
    FileCollection codenarcClasspath

    /**
     * The CodeNarc configuration file to use.
     */
    @InputFile
    File configFile

    /**
     * The maximum number of priority 1 violations allowed before failing the build.
     */
    @Input
    int maxPriority1Violations

    /**
     * The maximum number of priority 2 violations allowed before failing the build.
     */
    @Input
    int maxPriority2Violations

    /**
     * The maximum number of priority 3 violations allowed before failing the build.
     */
    @Input
    int maxPriority3Violations

    @Nested
    private final CodeNarcReportsImpl reports

    /**
     * Whether or not the build should break when the verifications performed by this task fail.
     */
    boolean ignoreFailures

    CodeNarc() {
        reports = instantiator.newInstance(CodeNarcReportsImpl, this)
    }

    @Inject
    Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void run() {
        logging.captureStandardOutput(LogLevel.INFO)
        def classpath = new DefaultClassPath(getCodenarcClasspath())
        antBuilder.withClasspath(classpath.asFiles).execute {
            ant.taskdef(name: 'codenarc', classname: 'org.codenarc.ant.CodeNarcTask')
            try {
                ant.codenarc(ruleSetFiles: "file:${getConfigFile()}", maxPriority1Violations: getMaxPriority1Violations(), maxPriority2Violations: getMaxPriority2Violations(), maxPriority3Violations: getMaxPriority3Violations()) {
                    reports.enabled.each { Report r ->
                        report(type: r.name) {
                            option(name: 'outputFile', value: r.destination)
                        }
                    }

                    source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                }
            } catch (Exception e) {
                if (e.message.matches('Exceeded maximum number of priority \\d* violations.*')) {
                    def message = "CodeNarc rule violations were found."
                    def report = reports.firstEnabled
                    if (report) {
                        def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.destination)
                        message += " See the report at: $reportUrl"
                    }
                    if (getIgnoreFailures()) {
                        logger.warn(message)
                        return
                    }
                    throw new GradleException(message, e)
                }
                throw e
            }
        }
    }

    /**
     * Returns the reports to be generated by this task.
     */
    CodeNarcReports getReports() {
        return reports
    }

    /**
     * Configures the reports to be generated by this task.
     */
    CodeNarcReports reports(Closure closure) {
        reports.configure(closure)
    }
}
