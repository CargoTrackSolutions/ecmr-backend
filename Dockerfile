FROM eclipse-temurin:21.0.9_10-jre-noble

#Update packages
RUN apt-get update && apt-get upgrade -y && apt-get clean && rm -rf /var/lib/apt/lists/*

ARG APPLICATION_VERSION=n.a
ENV APPLICATION_VERSION=$APPLICATION_VERSION

COPY startup.sh /root/startup.sh
RUN chmod +x /root/startup.sh \
&& mkdir /app \
&& mkdir /usr/local/cacerts
COPY /src/main/resources/eseal/test-keystore.jks /app

USER root
COPY target/*.jar /app/runme.jar

ENTRYPOINT ["/root/startup.sh"]
CMD ["/app/runme.jar"]
