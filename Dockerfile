# renovate: datasource=github-releases depName=microsoft/ApplicationInsights-Java
ARG APP_INSIGHTS_AGENT_VERSION=3.7.1
ARG PLATFORM=""

FROM hmctspublic.azurecr.io/base/java${PLATFORM}:21-distroless

# Change to non-root privilege
USER hmcts

COPY lib/applicationinsights.json /opt/app/
COPY build/libs/ccd-message-publisher.jar /opt/app/

EXPOSE 4456
CMD [ "ccd-message-publisher.jar" ]
