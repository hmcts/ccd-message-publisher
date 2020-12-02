package uk.gov.hmcts.ccd.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface MessageQueueCandidateRepository extends PagingAndSortingRepository<MessageQueueCandidateEntity, Long> {

    @Query("select m from MessageQueueCandidateEntity m where m.published is null "
        + "and m.messageType = :messageType order by m.timeStamp asc")
    Slice<MessageQueueCandidateEntity> findUnpublishedMessages(String messageType, Pageable pageable);
}
