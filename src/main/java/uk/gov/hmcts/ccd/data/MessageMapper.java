package uk.gov.hmcts.ccd.data;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageMapper {

    MessageDTO toMessageDto(MessageQueueCandidateEntity entity);
}
