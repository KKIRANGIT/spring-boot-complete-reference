FROM eclipse-temurin:21-jre

WORKDIR /app

COPY ./bankflow-api-gateway/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
