// Main entry point for the build pipeline
// Handles the overall pipeline flow and orchestration

def call(Map config) {
    // Initialize pipeline with configuration
    def pipeline = new org.example.PipelineConfig(config)
    def utils = new org.example.Utils()
    
    try {
        // Validate configuration
        pipeline.validate()
        
        // Execute pipeline stages
        pipeline.stage('Initialization') {
            echo "Starting pipeline for ${pipeline.appName} v${pipeline.version}"
            utils.printBuildInfo()
        }
        
        // Source code management
        pipeline.stage('SCM') {
            checkout([
                $class: 'GitSCM',
                branches: [[name: pipeline.scm.branch]],
                extensions: [[$class: 'CloneOption', depth: 1]],
                userRemoteConfigs: [[url: pipeline.scm.url]]
            ])
            
            // Set build environment variables
            env.GIT_COMMIT = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
            env.GIT_SHORT_COMMIT = env.GIT_COMMIT.take(8)
            env.BUILD_VERSION = "${pipeline.version}-${env.BUILD_NUMBER}-${env.GIT_SHORT_COMMIT}"
            
            // Verify code quality gates
            if (pipeline.qualityGates.enabled) {
                def qualityCheck = new org.example.QualityGate()
                qualityCheck.verify(pipeline.qualityGates)
            }
        }
        
        // Build stage
        def buildArtifacts = pipeline.stage('Build') {
            def builder = new org.example.BuildManager(pipeline.buildTool)
            return builder.build(pipeline.buildConfig)
        }
        
        // Test stages
        pipeline.parallel([
            'Unit Tests': {
                pipeline.stage('Unit Tests') {
                    def tester = new org.example.TestRunner()
                    tester.runUnitTests(pipeline.testConfig.unitTests)
                }
            },
            'Static Analysis': pipeline.testConfig.staticAnalysis ? {
                pipeline.stage('Static Analysis') {
                    def analyzer = new org.example.CodeAnalyzer()
                    analyzer.runStaticAnalysis(pipeline.testConfig.staticAnalysis)
                }
            } : null
        ].findAll { it.value != null })
        
        // Integration tests if enabled
        if (pipeline.testConfig.integrationTests.enabled) {
            pipeline.stage('Integration Tests') {
                def tester = new org.example.TestRunner()
                tester.runIntegrationTests(pipeline.testConfig.integrationTests)
            }
        }
        
        // Security scanning
        if (pipeline.security.scan.enabled) {
            pipeline.stage('Security Scan') {
                def scanner = new org.example.SecurityScanner()
                scanner.runScans(pipeline.security.scan)
                
                if (pipeline.security.failOnVulnerability) {
                    def report = scanner.getVulnerabilityReport()
                    if (report.critical > 0 || report.high > 0) {
                        error("Security scan found critical/high vulnerabilities")
                    }
                }
            }
        }
        
        // Performance testing if enabled
        if (pipeline.performance.enabled) {
            pipeline.stage('Performance Test') {
                def perfTester = new org.example.PerformanceTester()
                def results = perfTester.runTests(pipeline.performance.config)
                
                // Archive performance results
                perfTester.archiveResults(results, pipeline.performance.artifactsPath)
                
                // Fail if performance thresholds are not met
                if (pipeline.performance.failOnThreshold && !perfTester.meetsThresholds(results, pipeline.performance.thresholds)) {
                    error("Performance tests did not meet the required thresholds")
                }
            }
        }
        
        // Artifact publishing
        pipeline.stage('Publish Artifacts') {
            def publisher = new org.example.ArtifactPublisher()
            publisher.publish(buildArtifacts, pipeline.artifacts)
            
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
        
        // Final notification
        pipeline.stage('Notify') {
            def notifier = new org.example.Notifier()
            notifier.sendSuccess(pipeline.notifications, [
                appName: pipeline.appName,
                version: pipeline.version,
                buildNumber: env.BUILD_NUMBER,
                commit: env.GIT_SHORT_COMMIT,
                buildUrl: env.BUILD_URL
            ])
        }
        
    } catch (Exception e) {
        // Handle failures
        currentBuild.result = 'FAILURE'
        
        // Send failure notification if configured
        if (pipeline?.notifications?.onFailure) {
            try {
                def notifier = new org.example.Notifier()
                notifier.sendFailure(pipeline.notifications, [
                    appName: pipeline.appName,
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
        pipeline.stage('Cleanup') {
            echo "Cleaning up workspace..."
            // Add any cleanup steps here
        }
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    call(config)
}
