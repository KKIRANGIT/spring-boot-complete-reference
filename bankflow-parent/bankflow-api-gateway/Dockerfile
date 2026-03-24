FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom files first so Maven dependency downloads stay cached between builds.
COPY bankflow-parent/pom.xml bankflow-parent/pom.xml
COPY bankflow-parent/bankflow-common/pom.xml bankflow-parent/bankflow-common/pom.xml
COPY bankflow-parent/bankflow-api-gateway/pom.xml bankflow-parent/bankflow-api-gateway/pom.xml

RUN mvn -f bankflow-parent/pom.xml dependency:go-offline -B \
    -pl bankflow-common,bankflow-api-gateway -am

COPY bankflow-parent/bankflow-common/src bankflow-parent/bankflow-common/src
COPY bankflow-parent/bankflow-api-gateway/src bankflow-parent/bankflow-api-gateway/src

RUN mvn -f bankflow-parent/pom.xml package -DskipTests \
    -pl bankflow-common,bankflow-api-gateway -am

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/bankflow-parent/bankflow-api-gateway/target/*.jar app.jar

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=local", "-jar", "app.jar"]
