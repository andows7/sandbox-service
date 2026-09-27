FROM openjdk:17-jdk-slim
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

WORKDIR /app

# Copy the built jar to the image
COPY target/sandbox-service-1.0.0-SNAPSHOT.jar app.jar

# Make sure we have Docker CLI installed since docker-java might need it 
# or just communicate via unix socket. For docker-java unix socket is enough.
# Expose the application port
EXPOSE 8084

# Set entrypoint
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
