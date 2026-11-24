pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        APP_NAME        = 'e-comm-backend'
        IMAGE_NAME      = 'e-comm-backend:latest'
        APP_PORT        = '8002'
        DOCKER_NETWORK  = 'jenkins-mysql_default'

        DB_HOST         = 'mysql-8081'
        DB_PORT         = '3306'
        DB_NAME         = 'e-comm'
        DB_USER         = 'e-comm'
        DB_PASSWORD     = 'Pass@123'

        DB_URL          = "jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8"
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
                    # 1. Ensure e-comm database and user permissions exist in mysql-8081
                    docker exec -i mysql-8081 mysql -u root -pAppSecure_2026_X9! -e "CREATE DATABASE IF NOT EXISTS \\\`$DB_NAME\\\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD'; GRANT ALL PRIVILEGES ON \\\`$DB_NAME\\\`.* TO '$DB_USER'@'%'; FLUSH PRIVILEGES;" 2>/dev/null || \
                    docker exec -i mysql-8081 mysql -u root -e "CREATE DATABASE IF NOT EXISTS \\\`$DB_NAME\\\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD'; GRANT ALL PRIVILEGES ON \\\`$DB_NAME\\\`.* TO '$DB_USER'@'%'; FLUSH PRIVILEGES;" 2>/dev/null || true

                    # 2. Stop previous container if running
                    docker rm -f "$APP_NAME" || true

                    # 3. Start new container on the MySQL Docker network
                    docker run -d \
                        --name "$APP_NAME" \
                        --restart unless-stopped \
                        --network "$DOCKER_NETWORK" \
                        -p "$APP_PORT:8002" \
                        -e SPRING_DATASOURCE_URL="$DB_URL" \
                        -e SPRING_DATASOURCE_USERNAME="$DB_USER" \
                        -e SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
                        -e DB_HOST="$DB_HOST" \
                        -e DB_PORT="$DB_PORT" \
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
