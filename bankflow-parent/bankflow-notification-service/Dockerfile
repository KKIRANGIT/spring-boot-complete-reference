FROM eclipse-temurin:21-jre

WORKDIR /app

COPY ./bankflow-notification-service/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
