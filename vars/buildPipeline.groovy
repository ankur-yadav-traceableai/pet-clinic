// Main entry point for the build pipeline
// Handles the overall pipeline flow and orchestration

def call(Map config) {
    // Initialize pipeline with configuration
    def pipeline = new org.example.PipelineConfig(config)
    def utils = new org.example.Utils(this)
    
    try {
        // Validate configuration
        pipeline.validate()
        
        // Initialize stage
        stage('Initialize') {
            echo "Starting pipeline for ${pipeline.appName} v${pipeline.version}"
            
            // Ensure JAVA_HOME is set correctly
            try {
                if (!env.JAVA_HOME || !fileExists("${env.JAVA_HOME}/bin/java")) {
                    def javaBin = sh(script: 'which java', returnStdout: true).trim()
                    if (javaBin) {
                        def javaHome = sh(script: 'dirname "$(dirname "' + javaBin + '")"', returnStdout: true).trim()
                        env.JAVA_HOME = javaHome
                        env.PATH = "${javaHome}/bin:${env.PATH}"
                    }
                }
                sh 'java -version'
            } catch (e) {
                echo "Warning: Unable to validate JAVA_HOME automatically: ${e.message}"
            }
            
            utils.printBuildInfo()
            
            // Set build display name
            currentBuild.displayName = "${pipeline.version}-${env.BUILD_NUMBER}"
        }
        
        // Checkout stage
        stage('Checkout') {
            checkout([
                $class: 'GitSCM',
                branches: [[name: pipeline.scm.branch]],
                extensions: [
                    [$class: 'CloneOption', depth: 1, noTags: false, reference: '', shallow: true],
                    [$class: 'CleanBeforeCheckout']
                ],
                userRemoteConfigs: [[
                    url: pipeline.scm.url,
                    credentialsId: pipeline.scm.credentialsId ?: null
                ]]
            ])
            
            // Set build environment variables
            env.GIT_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
            env.GIT_SHORT_COMMIT = env.GIT_COMMIT.take(8)
            env.GIT_BRANCH = pipeline.scm.branch
            env.BUILD_VERSION = "${pipeline.version}-${env.BUILD_NUMBER}-${env.GIT_SHORT_COMMIT}"
            env.APP_NAME = pipeline.appName
            env.APP_VERSION = pipeline.version
            
            echo "Building commit: ${env.GIT_COMMIT} on branch: ${env.GIT_BRANCH}"
            echo "Repository: ${pipeline.scm.url}"
        }
        
        // Build stage
        def buildArtifacts = []
        stage('Build') {
            def builder = new org.example.BuildManager(pipeline.buildTool, this)
            
            // Configure build settings
            def buildConfig = pipeline.buildConfig + [
                parallel: pipeline.buildConfig.parallel ?: true,
                skipTests: !pipeline.testConfig.unitTests.enabled,
                skipBuildStaticChecks: pipeline.buildConfig.skipBuildStaticChecks ?: true,
                appName: pipeline.appName
            ]
            
            buildArtifacts = builder.build(buildConfig)
        }
        
        // Unit Tests stage
        if (pipeline.testConfig.unitTests.enabled) {
            stage('Unit Tests') {
                def tester = new org.example.TestRunner(this)
                tester.runUnitTests(pipeline.testConfig.unitTests)
            }
        }
        
        // Integration tests if enabled
        if (pipeline.testConfig.integrationTests.enabled) {
            stage('Integration Tests') {
                def tester = new org.example.TestRunner(this)
                tester.runIntegrationTests(pipeline.testConfig.integrationTests)
            }
        }
        
        // Code Quality stage
        if (pipeline.testConfig.staticAnalysis.enabled) {
            stage('Code Quality') {
                def analyzer = new org.example.CodeAnalyzer(this)
                analyzer.runStaticAnalysis(pipeline.testConfig.staticAnalysis)
            }
        }
        
        // Security scanning
        if (pipeline.security.scan.enabled) {
            stage('Security Scan') {
                def scanner = new org.example.SecurityScanner(this)
                
                // Pass appName to security scanner config
                def securityConfig = pipeline.security.scan + [appName: pipeline.appName]
                scanner.runScans(securityConfig)
                
                if (pipeline.security.scan.failOnVulnerability) {
                    def report = scanner.getVulnerabilityReport()
                    if (report.critical > 0 || report.high > 0) {
                        error("Security scan found critical/high vulnerabilities")
                    }
                }
            }
        }
        
        // Generate Documentation if enabled
        if (pipeline.buildConfig.generateDocs) {
            stage('Generate Documentation') {
                if (pipeline.buildTool == 'maven') {
                    def docArgs = []
                    if (pipeline.buildConfig.skipBuildStaticChecks) {
                        docArgs += [
                            '-Dcheckstyle.skip=true',
                            '-Dpmd.skip=true',
                            '-Dspotbugs.skip=true',
                            '-Dcheckstyle.failOnViolation=false',
                            '-Dpmd.failOnViolation=false',
                            '-Dspotbugs.failOnError=false',
                            '-Dnohttp.skip=true',
                            '-Dnohttp.check.skip=true',
                            '-DskipChecks',
                            '-Dnohttp=false'
                        ]
                    }
                    sh "mvn javadoc:javadoc ${docArgs.join(' ')}"
                    
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'target/site/apidocs',
                        reportFiles: 'index.html',
                        reportName: 'JavaDoc',
                        reportTitles: 'API Documentation'
                    ])
                } else {
                    sh './gradlew javadoc --no-daemon'
                    
                    publishHTML([
                        allowMissing: true,
                        alwaysLinkToLastBuild: true,
                        keepAll: true,
                        reportDir: 'build/docs/javadoc',
                        reportFiles: 'index.html',
                        reportName: 'JavaDoc',
                        reportTitles: 'API Documentation'
                    ])
                }
            }
        }
        
        // Artifact publishing
        if (pipeline.artifacts.publish) {
            stage('Publish Artifacts') {
                // Generate build info
                def buildInfo = [
                    buildNumber: env.BUILD_NUMBER,
                    version: env.BUILD_VERSION,
                    commit: env.GIT_COMMIT,
                    timestamp: new Date().format("yyyy-MM-dd'T'HH:mm:ssZ"),
                    artifacts: buildArtifacts.collect { it.name },
                    metadata: pipeline.metadata
                ]
                
                // Save build info as artifact
                writeJSON file: 'build-info.json', json: buildInfo
                archiveArtifacts artifacts: 'build-info.json', allowEmptyArchive: false
                
                // Update build display name
                currentBuild.displayName = "#${env.BUILD_NUMBER} - ${pipeline.appName} v${pipeline.version}"
                currentBuild.description = "Commit: ${env.GIT_SHORT_COMMIT}"
            }
        }
        
        // Final notification
        if (pipeline.notifications.onSuccess) {
            stage('Notify') {
                def notifier = new org.example.Notifier(this)
                notifier.sendSuccess(pipeline.notifications, [
                    appName: pipeline.appName,
                    version: pipeline.version,
                    buildNumber: env.BUILD_NUMBER,
                    commit: env.GIT_SHORT_COMMIT,
                    buildUrl: env.BUILD_URL
                ])
            }
        }
        
    } catch (Exception e) {
        // Handle failures
        currentBuild.result = 'FAILURE'
        
        // Send failure notification if configured
        if (pipeline?.notifications?.onFailure) {
            try {
                def notifier = new org.example.Notifier(this)
                notifier.sendFailure(pipeline.notifications, [
                    appName: pipeline.appName ?: 'Unknown',
                    buildNumber: env.BUILD_NUMBER,
                    error: e.message,
                    buildUrl: env.BUILD_URL
                ])
            } catch (notifyError) {
                echo "Failed to send failure notification: ${notifyError.message}"
            }
        }
        
        // Re-throw to mark build as failed
        throw e
    } finally {
        // Always run cleanup
        stage('Cleanup') {
            echo "Cleaning up workspace..."
            try {
                deleteDir()
            } catch (e) {
                echo "Workspace cleanup skipped: ${e.message}"
            }
        }
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    call(config)
}
