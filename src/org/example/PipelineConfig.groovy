package org.example

/**
 * Configuration class for the pipeline
 * Handles validation and default values for pipeline configuration
 */
class PipelineConfig implements Serializable {
    // Core configuration
    String appName
    String version
    String buildTool = 'maven'  // maven, gradle
    
    // SCM configuration
    Map scm = [
        url: '',
        branch: 'main',
        credentialsId: ''
    ]
    
    // Build configuration
    Map buildConfig = [
        sourceCompatibility: '11',
        targetCompatibility: '11',
        buildArgs: [],
        environment: [:],
        jvmOpts: '',
        parallel: true,
        skipBuildStaticChecks: true,
        generateDocs: false
    ]
    
    // Test configuration
    Map testConfig = [
        unitTests: [
            enabled: true,
            skip: false,
            parallel: true,
            forkCount: 1,
            includes: ['**/*Test.class'],
            excludes: ['**/*IT.class']
        ],
        integrationTests: [
            enabled: false,
            includes: ['**/*IT.class'],
            excludes: [],
            systemProperties: [:],
            environment: [:]
        ],
        staticAnalysis: [
            enabled: true,
            tools: ['checkstyle', 'pmd', 'spotbugs'],
            qualityGates: [
                maxCritical: 0,
                maxHigh: 5,
                maxTotal: 20
            ]
        ]
    ]
    
    // Security configuration
    Map security = [
        scan: [
            enabled: false,
            tools: ['owasp', 'dependency-check'],
            failOnVulnerability: true,
            excludePatterns: []
        ],
        credentials: [:]  // Store any required credentials
    ]
    
    // Performance testing
    Map performance = [
        enabled: false,
        tool: 'jmeter',  // jmeter, gatling, etc.
        config: [:],
        thresholds: [:],
        failOnThreshold: true,
        artifactsPath: 'performance-reports'
    ]
    
    // Artifacts configuration
    Map artifacts = [
        publish: true,
        repository: [
            type: 'nexus',  // nexus, artifactory, s3, etc.
            url: '',
            credentialsId: ''
        ],
        include: ['**/target/*.jar', '**/build/libs/*.jar'],
        exclude: ['**/*-sources.jar', '**/*-javadoc.jar']
    ]
    
    // Quality gates
    Map qualityGates = [
        enabled: true,
        sonar: [
            enabled: true,
            serverUrl: 'http://sonar:9000',
            qualityGateWait: 300,  // seconds
            timeout: 10            // minutes
        ],
        coverage: [
            minLineCoverage: 70.0,
            minBranchCoverage: 60.0
        ]
    ]
    
    // Notifications
    Map notifications = [
        onSuccess: true,
        onFailure: true,
        onUnstable: true,
        channels: ['console'],
        email: [
            recipients: 'team@example.com',
            sendToIndividuals: false
        ],
        slack: [
            channel: '#builds',
            notifySuccess: true,
            notifyFailure: true,
            notifyBackToNormal: true
        ]
    ]
    
    // Custom metadata
    Map metadata = [:]
    
    // Constructor with initial configuration
    PipelineConfig(Map config = [:]) {
        // Deep merge for nested maps
        config.each { key, value ->
            if (value instanceof Map && this.hasProperty(key) && this[key] instanceof Map) {
                this[key] = this[key] + value  // Merge maps
            } else if (this.hasProperty(key)) {
                this[key] = value
            }
        }
    }
    
    /**
     * Validate the configuration
     * @throws IllegalArgumentException if configuration is invalid
     */
    void validate() {
        if (!appName) {
            throw new IllegalArgumentException('appName is required')
        }
        if (!version) {
            throw new IllegalArgumentException('version is required')
        }
        if (!scm.url) {
            throw new IllegalArgumentException('scm.url is required')
        }
        
        // Validate build tool
        if (!['maven', 'gradle'].contains(buildTool)) {
            throw new IllegalArgumentException("Unsupported build tool: ${buildTool}")
        }
        
        // Validate test configuration
        if (testConfig.unitTests.enabled && testConfig.unitTests.parallel) {
            def cpuCores = Runtime.runtime.availableProcessors()
            if (testConfig.unitTests.forkCount > cpuCores * 2) {
                echo "Warning: forkCount (${testConfig.unitTests.forkCount}) is higher than recommended (${cpuCores * 2})"
            }
        }
        
        // Validate security configuration
        if (security.scan.enabled && !security.scan.tools) {
            throw new IllegalArgumentException('At least one security scan tool must be specified')
        }
    }
    
    /**
     * Execute a pipeline stage with the given name and body
     */
    def stage(String name, Closure body) {
        return {
            try {
                echo "Stage: ${name}"
                def startTime = System.currentTimeMillis()
                
                // Execute the stage body
                def result = body()
                
                // Calculate duration
                def duration = (System.currentTimeMillis() - startTime) / 1000
                echo "Stage '${name}' completed in ${duration} seconds"
                
                return result
            } catch (Exception e) {
                echo "Stage '${name}' failed: ${e.message}"
                throw e
            }
        }()
    }
    
    /**
     * Execute stages in parallel
     */
    def parallel(Map stages) {
        // Filter out null stages
        return stages.findAll { it.value != null }
    }
}
