# The jar is built on the host (make service-build) so the image build needs no network access
# and no second Maven repository.
FROM eclipse-temurin:21-jre

WORKDIR /opt/settlement
COPY target/settlement-service-0.1.0.jar /opt/settlement/settlement-service.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/opt/settlement/settlement-service.jar"]
