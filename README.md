# Jenkins Pipeline Setup Guide

This repository contains a Jenkins pipeline for building and testing the Spring PetClinic application. The pipeline supports both declarative (`Jenkinsfile`) and scripted (`Jenkinsfile-var`) configurations using a shared library.

## Prerequisites

### System Requirements
- Jenkins 2.346.1 or later
- Linux/macOS/Windows agent with Docker support
- At least 4GB RAM, 2 CPUs recommended

### Required Tools (Manual Installation)

#### 1. Java Development Kit (JDK)
- **Version**: OpenJDK 17
- **Installation**:
  ```bash
  # Ubuntu/Debian
  sudo apt update
  sudo apt install openjdk-17-jdk
  
  # macOS (with Homebrew)
  brew install openjdk@17
  
  # Or download from https://adoptium.net/
  ```
- **Configuration in Jenkins**: Add JDK installation named `jdk17` in Manage Jenkins → Global Tool Configuration

#### 2. Build Tools
Choose either Maven or Gradle:

**Maven**:
- **Version**: 3.8.x or later
- **Installation**:
  ```bash
  # Ubuntu/Debian
  sudo apt install maven
  
  # macOS
  brew install maven
  
  # Or download from https://maven.apache.org/download.cgi
  ```
- **Configuration in Jenkins**: Optional - add Maven installation named `maven3`

**Gradle**:
- **Version**: 7.x or later
- **Installation**:
  ```bash
  # Ubuntu/Debian
  sudo apt install gradle
  
  # macOS
  brew install gradle
  
  # Or download from https://gradle.org/releases/
  ```

#### 3. Docker
- **Version**: 20.10.x or later
- **Installation**:
  ```bash
  # Ubuntu/Debian
  sudo apt update
  sudo apt install docker.io
  sudo systemctl start docker
  sudo usermod -aG docker jenkins  # Allow Jenkins user to run Docker
  
  # macOS
  brew install --cask docker
  ```
- **Configuration**: Ensure Docker daemon is running and Jenkins can access it

#### 4. Security Scanning Tools

**Trivy** (Container vulnerability scanner):
```bash
# Ubuntu/Debian
sudo apt install wget apt-transport-https gnupg lsb-release
wget -qO - https://aquasecurity.github.io/trivy-repo/deb/public.key | sudo apt-key add -
echo "deb https://aquasecurity.github.io/trivy-repo/deb $(lsb_release -sc) main" | sudo tee -a /etc/apt/sources.list.d/trivy.list
sudo apt update
sudo apt install trivy

# macOS
brew install trivy
```

## Jenkins Setup

### 1. Install Jenkins
```bash
# Ubuntu/Debian
wget -q -O - https://pkg.jenkins.io/debian/jenkins.io.key | sudo apt-key add -
sudo sh -c 'echo deb http://pkg.jenkins.io/debian-stable binary/ > /etc/apt/sources.list.d/jenkins.list'
sudo apt update
sudo apt install jenkins
sudo systemctl start jenkins

# macOS (with Homebrew)
brew install jenkins
brew services start jenkins
```

### 2. Install Required Plugins
Use the plugin list from `jenkins-plugins.txt`. Install via:
- Jenkins Web UI: Manage Jenkins → Manage Plugins → Available
- Or use Jenkins CLI:
  ```bash
  java -jar jenkins-cli.jar -s http://localhost:8080/ install-plugin <plugin-id>
  ```

Key plugins:
- `workflow-aggregator` (Pipeline)
- `git` (Git integration)
- `docker-workflow` (Docker support)
- `dependency-check-jenkins-plugin` (OWASP Dependency-Check)
- `warnings-ng` (Code analysis reports)
- `jacoco` (Test coverage)
- `slack` (Notifications)

### 3. Configure Global Tools
In Manage Jenkins → Global Tool Configuration:

- **JDK**: Add JDK 17 installation, name it `jdk17`
- **Git**: Configure Git executable path
- **Docker**: Configure Docker installation if needed

### 4. Configure Security Tools
In Manage Jenkins → Global Tool Configuration:

- **OWASP Dependency-Check**: Add installation named `Dependency-Check`
  - Installation method: Install automatically
  - Or point to manual installation path

### 5. Set up Shared Library
The pipeline uses a shared library `advanced-complex-lib`. You have two options:

#### Option A: Use from GitHub (Recommended)
1. Go to Manage Jenkins → Configure System
2. Under "Global Pipeline Libraries", add:
   - Name: `advanced-complex-lib`
   - Default version: `main`
   - Retrieval method: Modern SCM
   - Source: Git
   - Repository URL: `https://github.com/your-org/advanced-complex-lib` (replace with actual repo)
   - Credentials: If private repository

#### Option B: Load from Local Directory
Copy the `vars/` and `src/` directories to your Jenkins shared library location.

### 6. Configure Credentials
Create the following credentials in Jenkins (Manage Jenkins → Manage Credentials):

- **Git Credentials** (if using private repos):
  - Type: Username with password
  - ID: `git-credentials` (or as specified in pipeline parameters)

- **Slack Token** (for notifications):
  - Type: Secret text
  - ID: `slack-token`
  - Configure Slack workspace in Manage Jenkins → Configure System

- **SNYK Token** (for Snyk scans):
  - Type: Secret text
  - ID: `snyk-token`

## Pipeline Configuration

### Using Jenkinsfile (Declarative Pipeline)
1. Copy `Jenkinsfile` to your repository root
2. Create a new Pipeline job in Jenkins
3. Configure SCM to point to your repository
4. Build the job

### Using Jenkinsfile-var (Scripted Pipeline with Shared Library)
1. Copy `Jenkinsfile-var` to your repository root
2. Ensure shared library is configured as above
3. Create a new Pipeline job
4. Configure SCM
5. The pipeline will call `buildPipeline()` from the shared library

## Pipeline Parameters

The pipeline accepts several parameters to customize behavior:

- `GIT_REPO_URL`: Repository URL (default: Spring PetClinic)
- `GIT_BRANCH`: Branch to build (default: main)
- `BUILD_TOOL`: maven or gradle
- `RUN_UNIT_TESTS`: Enable unit tests
- `RUN_INTEGRATION_TESTS`: Enable integration tests
- `RUN_CODE_ANALYSIS`: Enable static code analysis
- `RUN_SECURITY_SCAN`: Enable security scanning
- `GENERATE_DOCS`: Generate documentation
- `PARALLEL_BUILD`: Enable parallel builds
- `SLACK_CHANNEL`: Notification channel

## Troubleshooting

### Common Issues

1. **Java Version Mismatch**:
   - Ensure JDK 17 is correctly configured in Jenkins tools
   - Check `JAVA_HOME` environment variable

2. **Docker Permission Denied**:
   - Add Jenkins user to docker group: `sudo usermod -aG docker jenkins`
   - Restart Jenkins service

3. **Shared Library Not Found**:
   - Verify library name matches `@Library('advanced-complex-lib@main')`
   - Check library configuration in Jenkins

4. **Security Tools Not Found**:
   - Ensure tools are installed on Jenkins agents
   - Verify paths in Global Tool Configuration

5. **Plugin Dependencies**:
   - Some plugins require specific versions of others
   - Check Jenkins logs for missing dependencies

### Logs and Debugging

- Enable debug logging in pipeline by setting `logLevel` in shared library config
- Check Jenkins agent logs for tool installation issues
- Use `echo` statements in pipeline for debugging

## Additional Information

- **Test Coverage**: JaCoCo reports are generated for Maven/Gradle projects
- **Code Quality**: PMD, Checkstyle, and SpotBugs reports available
- **Security**: OWASP Dependency-Check, Trivy, and ZAP scans included
- **Notifications**: Slack integration for build status updates
- **Artifacts**: JAR files and reports are archived automatically

For more advanced configuration, refer to the shared library documentation and Jenkins Pipeline documentation.
