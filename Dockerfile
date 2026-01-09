# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src

# 安全措施：强制使用 example 配置作为打包默认值，防止本地敏感配置泄露到镜像中
RUN cp src/main/resources/application.properties.example src/main/resources/application.properties

RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# 创建日志目录并设置环境变量
RUN mkdir -p /app/logs && chmod 777 /app/logs
ENV SPRING_PROFILES_ACTIVE=prod

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
