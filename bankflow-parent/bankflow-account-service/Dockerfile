FROM eclipse-temurin:21-jre

WORKDIR /app

COPY ./bankflow-account-service/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
