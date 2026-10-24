package edu.campus.green;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EWasteItemRepository extends JpaRepository<EWasteItem, String> {
    List<EWasteItem> findAllByRecordActiveTrueOrderByRecordRecordedAtDesc();
    Optional<EWasteItem> findByRecordId(String recordId);
}
