FROM eclipse-temurin:21-jre

WORKDIR /app

COPY ./bankflow-auth-service/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
