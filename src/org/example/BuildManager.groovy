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
        def buildArgs = ['clean', 'package']
        
        // Parallel build
        if (config.parallel) {
            buildArgs << '-T 1C'
        }
        
        // Skip tests if configured
        if (config.skipTests) {
            buildArgs << '-DskipTests'
        }
        
        // Skip static checks during build
        if (config.skipBuildStaticChecks) {
            buildArgs << '-Dcheckstyle.skip=true'
            buildArgs << '-Dpmd.skip=true'
            buildArgs << '-Dspotbugs.skip=true'
            buildArgs << '-Dcheckstyle.failOnViolation=false'
            buildArgs << '-Dpmd.failOnViolation=false'
            buildArgs << '-Dspotbugs.failOnError=false'
            buildArgs << '-Dnohttp.skip=true'
            buildArgs << '-Dnohttp.check.skip=true'
            buildArgs << '-DskipChecks'
            buildArgs << '-Dnohttp=false'
        }
        
        // Add custom build arguments
        if (config.buildArgs) {
            buildArgs.addAll(config.buildArgs)
        }
        
        // Execute build
        script.sh "mvn ${buildArgs.join(' ')}"
        
        // Archive artifacts
        script.archiveArtifacts artifacts: '**/target/*.jar', allowEmptyArchive: true
        
        // Build Docker image for Trivy scan
        buildDockerImage(config)
        
        // Find and return artifacts
        return findArtifacts('**/target/*.jar')
    }
    
    /**
     * Build using Gradle
     */
    private List<Map> buildWithGradle(Map config) {
        def buildArgs = ['clean', 'build']
        
        // Parallel build
        if (config.parallel) {
            buildArgs << '--parallel'
            buildArgs << "--max-workers=${Runtime.runtime.availableProcessors()}"
        }
        
        // Skip tests if configured
        if (config.skipTests) {
            buildArgs << '-x test'
        }
        
        // Skip static checks during build
        if (config.skipBuildStaticChecks) {
            buildArgs << '-x checkstyleMain -x checkstyleTest'
            buildArgs << '-x pmdMain -x pmdTest'
            buildArgs << '-x spotbugsMain -x spotbugsTest'
        }
        
        // Add custom build arguments
        if (config.buildArgs) {
            buildArgs.addAll(config.buildArgs)
        }
        
        // Execute build
        script.sh "./gradlew ${buildArgs.join(' ')}"
        
        // Archive artifacts
        script.archiveArtifacts artifacts: '**/build/libs/*.jar', allowEmptyArchive: true
        
        // Build Docker image for Trivy scan
        buildDockerImage(config)
        
        // Find and return artifacts
        return findArtifacts('**/build/libs/*.jar')
    }
    
    /**
     * Build Docker image for security scanning
     */
    private void buildDockerImage(Map config) {
        def appName = config.appName ?: script.env.APP_NAME ?: 'app'
        
        script.echo "Building Docker image for ${appName}..."
        
        script.sh '''
            set -e
            # Find the built JAR (prefer Maven target jar, exclude sources/javadoc)
            JAR=$(find . -path '*/target/*.jar' ! -name '*sources*' ! -name '*javadoc*' | head -n1 || true)
            if [ -z "$JAR" ]; then
              echo "No JAR found under target directories" >&2
              exit 1
            fi
            echo "Using JAR: $JAR"

            # Normalize to app.jar at workspace root for Docker context
            cp "$JAR" app.jar

            # Generate Dockerfile
            cat > Dockerfile <<'EOF'
FROM openjdk:17-ea-oraclelinux7
WORKDIR /
COPY app.jar /app.jar
EXPOSE 8080
ENTRYPOINT java -jar /app.jar
EOF

            # Build Docker image using APP_NAME from environment
            docker build -t ${APP_NAME}:latest .
        '''
    }
    
    /**
     * Find build artifacts
     */
    private List<Map> findArtifacts(String pattern) {
        def artifacts = []
        
        script.echo "Looking for artifacts matching: ${pattern}"
        
        try {
            // Use shell command to find files
            def jarFiles = script.sh(script: "find . -path '${pattern}' 2>/dev/null || true", returnStdout: true).trim()
            
            if (jarFiles) {
                jarFiles.split('\n').each { path ->
                    if (path) {
                        def artifact = [
                            name: path.split('/').last(),
                            path: path
                        ]
                        
                        // Add file size
                        try {
                            def size = script.sh(script: "stat -f%z '${path}' 2>/dev/null || stat -c%s '${path}' 2>/dev/null || echo 0", returnStdout: true).trim()
                            artifact.size = size.toLong()
                        } catch (Exception e) {
                            script.echo "Warning: Failed to get size for ${path}: ${e.message}"
                        }
                        
                        // Add checksum
                        try {
                            def checksum = script.sh(script: "sha256sum '${path}' 2>/dev/null | cut -d' ' -f1 || shasum -a 256 '${path}' 2>/dev/null | cut -d' ' -f1 || echo 'N/A'", returnStdout: true).trim()
                            artifact.checksum = checksum
                        } catch (Exception e) {
                            script.echo "Warning: Failed to calculate checksum for ${path}: ${e.message}"
                        }
                        
                        artifacts << artifact
                        script.echo "Found artifact: ${path} (${artifact.size ?: 'unknown'} bytes)"
                    }
                }
            }
        } catch (Exception e) {
            script.echo "Error finding artifacts: ${e.message}"
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
