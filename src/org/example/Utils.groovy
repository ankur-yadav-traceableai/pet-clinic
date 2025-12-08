package org.example

/**
 * Utility methods for the pipeline
 */
class Utils implements Serializable {
    private final def script
    
    /**
     * Constructor
     */
    Utils(script = null) {
        this.script = script ?: this
    }
    
    /**
     * Print build information
     */
    void printBuildInfo() {
        def buildInfo = getBuildInfo()
        
        script.echo """
        ====== Build Information ======
        Application: ${buildInfo.appName}
        Version: ${buildInfo.version}
        Build Number: ${buildInfo.buildNumber}
        Build URL: ${buildInfo.buildUrl}
        Node: ${buildInfo.nodeName}
        Workspace: ${buildInfo.workspace}
        Java Version: ${buildInfo.javaVersion}
        Build Tool: ${buildInfo.buildTool} ${buildInfo.buildToolVersion}
        OS: ${buildInfo.os}
        CPU Cores: ${buildInfo.cpuCores}
        ==============================
        """
    }
    
    /**
     * Get build information
     */
    Map getBuildInfo() {
        return [
            appName: script.env.APP_NAME ?: script.currentBuild.fullProjectName.split('/')[0],
            version: script.env.APP_VERSION ?: '1.0.0',
            buildNumber: script.env.BUILD_NUMBER ?: '0',
            buildUrl: script.env.BUILD_URL ?: 'N/A',
            nodeName: script.env.NODE_NAME ?: 'master',
            workspace: script.env.WORKSPACE ?: script.pwd(),
            javaVersion: getJavaVersion(),
            buildTool: getBuildTool(),
            buildToolVersion: getBuildToolVersion(),
            os: getOsInfo(),
            cpuCores: Runtime.runtime.availableProcessors()
        ]
    }
    
    /**
     * Get Java version
     */
    private String getJavaVersion() {
        try {
            def javaVersion = script.sh(script: 'java -version 2>&1 | head -n 1', returnStdout: true).trim()
            return javaVersion.replace('"', '')
        } catch (Exception e) {
            script.echo "Warning: Failed to get Java version: ${e.message}"
            return 'Unknown'
        }
    }
    
    /**
     * Get build tool (Maven/Gradle)
     */
    private String getBuildTool() {
        if (script.fileExists('pom.xml')) {
            return 'Maven'
        } else if (script.fileExists('build.gradle') || script.fileExists('build.gradle.kts')) {
            return 'Gradle'
        }
        return 'Unknown'
    }
    
    /**
     * Get build tool version
     */
    private String getBuildToolVersion() {
        try {
            if (script.fileExists('pom.xml')) {
                def mvnVersion = script.sh(script: 'mvn --version 2>&1 | head -n 1', returnStdout: true).trim()
                return mvnVersion.replace('Apache Maven ', '').split(' ')[0]
            } else if (script.fileExists('build.gradle') || script.fileExists('build.gradle.kts')) {
                def gradleVersion = script.sh(script: './gradlew --version 2>&1 | grep Gradle', returnStdout: true).trim()
                return gradleVersion.split(' ')[1]
            }
        } catch (Exception e) {
            script.echo "Warning: Failed to get build tool version: ${e.message}"
        }
        return 'Unknown'
    }
    
    /**
     * Get OS information
     */
    private String getOsInfo() {
        try {
            if (script.isUnix()) {
                return script.sh(script: 'uname -a', returnStdout: true).trim()
            } else {
                return script.sh(script: 'systeminfo | findstr /B /C:"OS Name" /C:"OS Version"', returnStdout: true).trim()
            }
        } catch (Exception e) {
            script.echo "Warning: Failed to get OS info: ${e.message}"
            return System.getProperty('os.name') + ' ' + System.getProperty('os.version')
        }
    }
    
    /**
     * Check if the build is running on a specific branch
     */
    boolean isBranch(String branchName) {
        return script.env.GIT_BRANCH == "origin/${branchName}" || 
               script.env.BRANCH_NAME == branchName ||
               script.sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim() == branchName
    }
    
    /**
     * Get current Git branch
     */
    String getCurrentBranch() {
        return script.env.GIT_BRANCH ? script.env.GIT_BRANCH.replace('origin/', '') : 
               script.env.BRANCH_NAME ?: 
               script.sh(script: 'git rev-parse --abbrev-ref HEAD', returnStdout: true).trim()
    }
    
    /**
     * Get current Git commit hash
     */
    String getGitCommitHash() {
        return script.env.GIT_COMMIT ?: 
               script.sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
    }
    
    /**
     * Get short Git commit hash
     */
    String getShortGitCommitHash() {
        return getGitCommitHash().take(8)
    }
    
    /**
     * Get Git repository URL
     */
    String getGitRepoUrl() {
        return script.sh(script: 'git config --get remote.origin.url', returnStdout: true).trim()
    }
    
    /**
     * Get Git commit message
     */
    String getGitCommitMessage() {
        return script.sh(script: 'git log -1 --pretty=%B', returnStdout: true).trim()
    }
}

// Support for direct script execution (for testing)
if (runAsScript) {
    def utils = new Utils(this)
    utils.printBuildInfo()
}
