package org.example

/**
 * Handles test execution for the pipeline
 */
class TestRunner implements Serializable {
    private final def script
    
    /**
     * Constructor
     */
    TestRunner(script = null) {
        this.script = script ?: this
    }
    
    /**
     * Run unit tests
     * @param config Test configuration
     */
    void runUnitTests(Map config = [:]) {
        if (config.enabled == false) {
            script.echo "Unit tests are disabled"
            return
        }
        
        script.echo "Running unit tests..."
        
        try {
            def testCmd = []
            
            // Determine build tool
            def isMaven = script.fileExists('pom.xml')
            def isGradle = script.fileExists('build.gradle') || script.fileExists('build.gradle.kts')
            
            if (isMaven) {
                testCmd << 'mvn test'
                
                // Add test includes/excludes
                if (config.includes) {
                    testCmd << "-Dtest='${config.includes.join(",")}'"
                }
                
                // Parallel execution
                if (config.parallel) {
                    testCmd << "-Dparallel=methods"
                    testCmd << "-DthreadCount=${config.forkCount ?: 4}"
                }
                
            } else if (isGradle) {
                testCmd << './gradlew test --parallel'
                
                // Add test includes/excludes
                if (config.includes) {
                    testCmd << "--tests ${config.includes.join(' --tests ')}"
                }
                
                // Parallel execution
                if (config.parallel) {
                    testCmd << "--parallel"
                    testCmd << "--max-workers=${config.forkCount ?: 4}"
                }
            } else {
                throw new IllegalStateException("Could not determine build tool (Maven/Gradle)")
            }
            
            // Execute tests
            script.sh testCmd.join(' ')
            
            // Process test results
            processTestResults()
            
            script.echo "Unit tests completed successfully"
            
        } catch (Exception e) {
            // Archive test results even if tests fail
            processTestResults()
            script.error "Unit tests failed: ${e.message}"
        }
    }
    
    /**
     * Run integration tests
     * @param config Integration test configuration
     */
    void runIntegrationTests(Map config = [:]) {
        if (config.enabled == false) {
            script.echo "Integration tests are disabled"
            return
        }
        
        script.echo "Running integration tests..."
        
        try {
            def testCmd = []
            
            // Determine build tool
            def isMaven = script.fileExists('pom.xml')
            def isGradle = script.fileExists('build.gradle') || script.fileExists('build.gradle.kts')
            
            // Set up test environment
            def testEnv = config.environment ?: [:]
            testEnv['TEST_ENV'] = 'integration'
            
            script.withEnv(testEnv.collect { k, v -> "${k}=${v}" }) {
                if (isMaven) {
                    testCmd << 'mvn verify -Pintegration-tests -DskipUnitTests'
                    
                    // Add test includes/excludes
                    if (config.includes) {
                        testCmd << "-Dit.test='${config.includes.join(",")}'"
                    }
                    
                } else if (isGradle) {
                    testCmd << './gradlew integrationTest --tests "**/*IT.*"'
                    
                    // Add test includes/excludes
                    if (config.includes) {
                        testCmd << "--tests ${config.includes.join(' --tests ')}"
                    }
                } else {
                    throw new IllegalStateException("Could not determine build tool (Maven/Gradle)")
                }
                
                // Add system properties
                if (config.systemProperties) {
                    def props = config.systemProperties.collect { k, v -> "-D${k}=${v}" }
                    testCmd.addAll(props)
                }
                
                // Execute tests
                script.sh testCmd.join(' ')
            }
            
            // Process test results
            processTestResults('**/surefire-reports/*.xml', '**/failsafe-reports/*.xml')
            
            script.echo "Integration tests completed successfully"
            
        } catch (Exception e) {
            // Archive test results even if tests fail
            processTestResults('**/surefire-reports/*.xml', '**/failsafe-reports/*.xml')
            script.error "Integration tests failed: ${e.message}"
        }
    }
    
    /**
     * Process test results and generate reports
     * @param patterns File patterns for test result files
     */
    private void processTestResults(String... patterns) {
        try {
            def testResults = patterns ?: ['**/target/surefire-reports/*.xml', '**/build/test-results/**/*.xml']
            
            // Archive test results
            script.junit(
                testResults: testResults.join(','),
                allowEmptyResults: true,
                skipMarkingBuildUnstable: true
            )
            
            // Generate code coverage report if available
            generateCoverageReport()
            
        } catch (Exception e) {
            script.echo "Warning: Failed to process test results: ${e.message}"
        }
    }
    
    /**
     * Generate code coverage report if coverage tool is used
     */
    private void generateCoverageReport() {
        try {
            // Check for JaCoCo (Maven/Gradle)
            if (script.fileExists('target/jacoco.exec') || script.fileExists('build/jacoco/test.exec')) {
                script.echo "Generating JaCoCo coverage report..."
                
                if (script.fileExists('pom.xml')) {
                    script.sh 'mvn jacoco:report'
                    
                    // Publish HTML report
                    script.publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Report',
                        reportTitles: 'Code Coverage'
                    ])
                } else if (script.fileExists('build.gradle')) {
                    script.sh './gradlew jacocoTestReport'
                    
                    // Publish HTML report
                    script.publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'build/reports/jacoco/test/html',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Report',
                        reportTitles: 'Code Coverage'
                    ])
                }
            }
            
            // Check for Cobertura (legacy)
            if (script.fileExists('target/coverage.xml') || script.fileExists('build/reports/cobertura/coverage.xml')) {
                script.echo "Publishing Cobertura coverage report..."
                
                def coberturaFile = script.fileExists('target/coverage.xml') ? 
                    'target/coverage.xml' : 'build/reports/cobertura/coverage.xml'
                
                script.publishCobertura coberturaReportFile: coberturaFile
            }
            
        } catch (Exception e) {
            script.echo "Warning: Failed to generate coverage report: ${e.message}"
        }
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    def testRunner = new TestRunner(this)
    
    if (binding.variables.get('testType') == 'unit') {
        testRunner.runUnitTests(binding.variables.get('config') ?: [:]) 
    } else if (binding.variables.get('testType') == 'integration') {
        testRunner.runIntegrationTests(binding.variables.get('config') ?: [:]) 
    } else {
        testRunner.runUnitTests(binding.variables.get('config') ?: [:]) 
    }
}
