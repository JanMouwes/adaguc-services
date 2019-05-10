FROM registry.heroku.com/java-backend-adaguc/base

WORKDIR /config
COPY adaguc-services-config.xml /config/adaguc-services-config.xml
ENV EXTERNALADDRESS="localhost"
ENV ADAGUC_SERVICES_CONFIG="/config/adaguc-services-config.xml"
EXPOSE 80

ENV PORT 80

ENTRYPOINT /adaguc/start.sh
