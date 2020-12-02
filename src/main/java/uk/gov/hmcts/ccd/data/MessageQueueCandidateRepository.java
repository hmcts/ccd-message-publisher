package uk.gov.hmcts.ccd.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
public interface MessageQueueCandidateRepository extends PagingAndSortingRepository<MessageQueueCandidateEntity, Long> {

    @Query("select m from MessageQueueCandidateEntity m where m.published is null "
        + "and m.messageType = :messageType order by m.timeStamp asc")
    Slice<MessageQueueCandidateEntity> findUnpublishedMessages(String messageType, Pageable pageable);

    @Modifying
    @Query("delete from MessageQueueCandidateEntity m where m.messageType = :messageType "
        + "and m.published < :olderThanDate")
    int deletePublishedMessages(LocalDateTime olderThanDate, String messageType);
}
