pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME = 'e-comm-backend'
        IMAGE_NAME = 'e-comm-backend:latest'
        APP_PORT = '8002'
        DB_NAME = 'e-comm'
        DB_USER = 'e-comm'
        DB_PASSWORD = 'Pass@123'
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/prem21kumars/e-commbackend.git'
            }
        }

        stage('Build and Test') {
            steps {
                sh '''
                    if [ -f mvnw ]; then
                        chmod +x mvnw
                        ./mvnw clean verify
                    elif command -v mvn >/dev/null 2>&1; then
                        mvn clean verify
                    else
                        echo "Maven not found on host, building using Docker container..."
                        docker run --rm -v "$(pwd):/app" -w /app maven:3.9-eclipse-temurin-21 mvn clean verify
                    fi
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t "$IMAGE_NAME" .'
            }
        }

        stage('Deploy Backend') {
            steps {
                sh '''
                    docker rm -f "$APP_NAME" || true

                    docker run -d \
                        --name "$APP_NAME" \
                        --restart unless-stopped \
                        --network host \
                        -e DB_HOST=127.0.0.1 \
                        -e DB_PORT=3306 \
                        -e DB_NAME="$DB_NAME" \
                        -e DB_USER="$DB_USER" \
                        -e DB_PASSWORD="$DB_PASSWORD" \
                        -e SERVER_PORT="$APP_PORT" \
                        -e RABBITMQ_ENABLED=false \
                        -e spring.profiles.active=dev \
                        "$IMAGE_NAME"
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                sh '''
                    sleep 10
                    docker ps --filter "name=$APP_NAME"
                    docker logs --tail 50 "$APP_NAME"
                    curl -f -s http://localhost:$APP_PORT/api/v1/ping || true
                '''
            }
        }
    }

    post {
        success {
            echo 'E-Commerce Backend CI/CD completed successfully!'
        }
        failure {
            echo 'Pipeline failed. Check Console Output.'
        }
    }
}
