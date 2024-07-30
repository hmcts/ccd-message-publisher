package uk.gov.hmcts.ccd.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
public interface MessageQueueCandidateRepository extends PagingAndSortingRepository<MessageQueueCandidateEntity, Long> {

    @Query("select m from MessageQueueCandidateEntity m where m.published is null "
        + "and m.messageType = :messageType order by m.timeStamp asc")
    Slice<MessageQueueCandidateEntity> findUnpublishedMessages(@Param("messageType") String messageType,
                                                               Pageable pageable);

    @Modifying
    @Query("delete from MessageQueueCandidateEntity m where m.messageType = :messageType "
        + "and m.published < :olderThanDate")
    int deletePublishedMessages(@Param("olderThanDate") LocalDateTime olderThanDate,
                                @Param("messageType") String messageType);

    Iterable<MessageQueueCandidateEntity> findAll();

    <S extends MessageQueueCandidateEntity> Iterable<S> saveAll(Iterable<MessageQueueCandidateEntity> entities);

}
