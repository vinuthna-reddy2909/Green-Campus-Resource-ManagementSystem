package edu.campus.green;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionHistoryRepository extends JpaRepository<CollectionHistory, String> {
    List<CollectionHistory> findByRecordIdOrderByOccurredAtDesc(String recordId);
}
