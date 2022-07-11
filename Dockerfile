ARG APP_INSIGHTS_AGENT_VERSION=2.5.1
ARG PLATFORM=""

FROM hmctspublic.azurecr.io/base/java${PLATFORM}:11-distroless

# Change to non-root privilege
USER hmcts

COPY lib/AI-Agent.xml /opt/app/
COPY build/libs/ccd-message-publisher.jar /opt/app/

EXPOSE 4456
CMD [ "ccd-message-publisher.jar" ]
