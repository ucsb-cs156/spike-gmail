# Build stage: Maven on Eclipse Temurin JDK 25 (Ubuntu noble).
# Keep the Java version here in sync with .java-version, .sdkmanrc,
# system.properties and <java.version> in pom.xml.
FROM maven:3.9.16-eclipse-temurin-25-noble AS builder

WORKDIR /home/app

# Copy the pom first so that dependency downloads are cached by Docker
# unless the pom changes.
COPY pom.xml .
RUN mvn -B -Pproduction dependency:go-offline

COPY . .

RUN mvn -B -Pproduction -DskipTests clean package

# Runtime stage: Java 25 JRE only (Ubuntu noble, so startup.sh can use GNU cut)
FROM eclipse-temurin:25-jre-noble

WORKDIR /home/app

COPY --from=builder /home/app/startup.sh /home/app/startup.sh
COPY --from=builder /home/app/target/spike-gmail-1.0.0.jar /home/app/target/spike-gmail-1.0.0.jar

RUN chmod +x /home/app/startup.sh

# Verify installation
RUN java -version

ENTRYPOINT ["/home/app/startup.sh","/home/app/target/spike-gmail-1.0.0.jar"]
