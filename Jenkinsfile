pipeline {
  agent any

  tools {
    jdk 'jdk17'
    maven 'maven3'
  }

  environment {
    IMAGE_NAME = 'decision-simulation-engine'
  }

  stages {
    stage('Backend Test') {
      steps {
        dir('backend') {
          sh 'mvn test'
        }
      }
    }

    stage('Frontend Build') {
      steps {
        dir('frontend') {
          sh 'npm ci || npm install'
          sh 'npm run build'
        }
      }
    }

    stage('Docker Build') {
      steps {
        sh 'docker build -t $IMAGE_NAME-backend:ci backend'
        sh 'docker build -t $IMAGE_NAME-frontend:ci frontend'
      }
    }
  }

  post {
    always {
      archiveArtifacts artifacts: 'backend/target/*.jar', fingerprint: true, allowEmptyArchive: true
      junit 'backend/target/surefire-reports/*.xml'
    }
  }
}
