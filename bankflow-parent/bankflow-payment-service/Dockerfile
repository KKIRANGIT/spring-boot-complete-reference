FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom files first so Docker can reuse the dependency layer until a pom.xml changes.
COPY bankflow-parent/pom.xml bankflow-parent/pom.xml
COPY bankflow-parent/bankflow-common/pom.xml bankflow-parent/bankflow-common/pom.xml
COPY bankflow-parent/bankflow-payment-service/pom.xml bankflow-parent/bankflow-payment-service/pom.xml

RUN mvn -f bankflow-parent/pom.xml dependency:go-offline -B \
    -pl bankflow-common,bankflow-payment-service -am

COPY bankflow-parent/bankflow-common/src bankflow-parent/bankflow-common/src
COPY bankflow-parent/bankflow-payment-service/src bankflow-parent/bankflow-payment-service/src

RUN mvn -f bankflow-parent/pom.xml package -DskipTests \
    -pl bankflow-common,bankflow-payment-service -am

FROM eclipse-temurin:17-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/bankflow-parent/bankflow-payment-service/target/*.jar app.jar

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=local", "-jar", "app.jar"]
