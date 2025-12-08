package org.example

/**
 * Handles build operations for different build tools
 */
class BuildManager implements Serializable {
    private final String buildTool
    private final def script
    
    /**
     * Constructor
     * @param buildTool The build tool to use (maven, gradle)
     */
    BuildManager(String buildTool, script = null) {
        this.buildTool = buildTool?.toLowerCase()
        this.script = script ?: this
        
        if (!['maven', 'gradle'].contains(this.buildTool)) {
            throw new IllegalArgumentException("Unsupported build tool: ${buildTool}")
        }
    }
    
    /**
     * Execute the build process
     * @param config Build configuration
     * @return List of build artifacts
     */
    List<Map> build(Map config = [:]) {
        def artifacts = []
        
        script.echo "Building with ${buildTool}..."
        
        try {
            switch (buildTool) {
                case 'maven':
                    artifacts = buildWithMaven(config)
                    break
                case 'gradle':
                    artifacts = buildWithGradle(config)
                    break
            }
            
            script.echo "Build completed successfully"
            return artifacts
        } catch (Exception e) {
            script.error "Build failed: ${e.message}"
        }
    }
    
    /**
     * Build using Maven
     */
    private List<Map> buildWithMaven(Map config) {
        def mvnCmd = "mvn"
        def goals = ["clean", "package"]
        
        // Add JVM options if specified
        def jvmOpts = config.jvmOpts ?: "-Xmx2g -XX:MaxPermSize=512m"
        
        // Build command with options
        def cmd = ["JAVA_OPTS=${jvmOpts}", mvnCmd]
        
        // Add build arguments
        if (config.sourceCompatibility) {
            cmd << "-Dmaven.compiler.source=${config.sourceCompatibility}"
        }
        if (config.targetCompatibility) {
            cmd << "-Dmaven.compiler.target=${config.targetCompatibility}"
        }
        
        // Add custom build arguments
        if (config.buildArgs) {
            cmd.addAll(config.buildArgs)
        }
        
        // Execute build
        script.sh cmd.join(' ')
        
        // Find and return artifacts
        return findArtifacts('**/target/*.jar')
    }
    
    /**
     * Build using Gradle
     */
    private List<Map> buildWithGradle(Map config) {
        def gradleCmd = "./gradlew"
        def tasks = ["clean", "build"]
        
        // Add JVM options if specified
        def jvmOpts = config.jvmOpts ?: "-Xmx2g -Dorg.gradle.daemon=false"
        
        // Build command with options
        def cmd = ["JAVA_OPTS=${jvmOpts}", gradleCmd]
        
        // Add build arguments
        if (config.sourceCompatibility) {
            cmd << "-Porg.gradle.java.home=${config.javaHome}"
            cmd << "-Porg.gradle.jvmargs=${jvmOpts}"
        }
        
        // Add custom build arguments
        if (config.buildArgs) {
            cmd.addAll(config.buildArgs)
        }
        
        // Add tasks
        cmd.addAll(tasks)
        
        // Execute build
        script.sh cmd.join(' ')
        
        // Find and return artifacts
        return findArtifacts('**/build/libs/*.jar')
    }
    
    /**
     * Find build artifacts
     */
    private List<Map> findArtifacts(String pattern) {
        def artifacts = []
        
        script.echo "Looking for artifacts matching: ${pattern}"
        
        // Find files matching the pattern
        def files = script.findFiles(glob: pattern)
        
        // Create artifact entries
        files.each { file ->
            def artifact = [
                name: file.name,
                path: file.path,
                size: file.length(),
                lastModified: new Date(file.lastModified())
            ]
            
            // Add checksum if available
            try {
                def checksum = script.sh(script: "sha256sum ${file.path} | cut -d' ' -f1", returnStdout: true).trim()
                artifact.checksum = checksum
            } catch (Exception e) {
                script.echo "Warning: Failed to calculate checksum for ${file.name}: ${e.message}"
            }
            
            artifacts << artifact
            script.echo "Found artifact: ${file.path} (${file.length()} bytes)"
        }
        
        if (artifacts.isEmpty()) {
            script.echo "Warning: No artifacts found matching pattern: ${pattern}"
        }
        
        return artifacts
    }
    
    /**
     * Get build information
     */
    Map getBuildInfo() {
        def buildInfo = [
            tool: buildTool,
            version: getBuildToolVersion(),
            javaVersion: getJavaVersion(),
            systemInfo: getSystemInfo()
        ]
        
        return buildInfo
    }
    
    /**
     * Get build tool version
     */
    private String getBuildToolVersion() {
        try {
            def versionCmd = (buildTool == 'maven') ? 'mvn --version' : './gradlew --version'
            def versionOutput = script.sh(script: versionCmd, returnStdout: true).trim()
            return versionOutput.split('\n').first()
        } catch (Exception e) {
            script.echo "Warning: Failed to get ${buildTool} version: ${e.message}"
            return 'unknown'
        }
    }
    
    /**
     * Get Java version
     */
    private String getJavaVersion() {
        try {
            return script.sh(script: 'java -version 2>&1 | head -n 1', returnStdout: true).trim()
        } catch (Exception e) {
            script.echo "Warning: Failed to get Java version: ${e.message}"
            return 'unknown'
        }
    }
    
    /**
     * Get system information
     */
    private Map getSystemInfo() {
        return [
            os: script.sh(script: 'uname -a', returnStdout: true).trim(),
            cpuCores: Runtime.runtime.availableProcessors(),
            memory: script.sh(script: 'free -h', returnStdout: true).trim(),
            diskSpace: script.sh(script: 'df -h', returnStdout: true).trim()
        ]
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    def buildManager = new BuildManager(binding.variables.get('buildTool'), this)
    return buildManager.build(binding.variables.get('config') ?: [:])
}
