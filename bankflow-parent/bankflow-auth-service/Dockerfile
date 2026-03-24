FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# Copy pom files first so Docker can cache dependency downloads until pom.xml files change.
# This avoids re-downloading the full Maven dependency graph on every source-only rebuild.
COPY bankflow-parent/pom.xml bankflow-parent/pom.xml
COPY bankflow-parent/bankflow-common/pom.xml bankflow-parent/bankflow-common/pom.xml
COPY bankflow-parent/bankflow-auth-service/pom.xml bankflow-parent/bankflow-auth-service/pom.xml

RUN mvn -f bankflow-parent/pom.xml dependency:go-offline -B \
    -pl bankflow-common,bankflow-auth-service -am

COPY bankflow-parent/bankflow-common/src bankflow-parent/bankflow-common/src
COPY bankflow-parent/bankflow-auth-service/src bankflow-parent/bankflow-auth-service/src

RUN mvn -f bankflow-parent/pom.xml package -DskipTests \
    -pl bankflow-common,bankflow-auth-service -am

FROM eclipse-temurin:17-jre-alpine
# Runtime image uses a JRE, not a full JDK, to keep the attack surface and image size smaller.
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=builder /app/bankflow-parent/bankflow-auth-service/target/*.jar app.jar

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-Dspring.profiles.active=local", "-jar", "app.jar"]

# JVM flags:
# - UseG1GC keeps pause times lower for latency-sensitive request processing.
# - MaxRAMPercentage=75 lets the JVM use most of the container instead of the conservative default.
# - egd=/dev/./urandom avoids container entropy blocking during JWT and crypto operations.
