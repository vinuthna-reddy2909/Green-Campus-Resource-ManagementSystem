package edu.campus.green;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface WasteRecordRepository extends JpaRepository<WasteRecord, String>, JpaSpecificationExecutor<WasteRecord> {
    List<WasteRecord> findByActiveTrueOrderByRecordedAtDesc();
    List<WasteRecord> findByActiveTrueAndCollectionStatus(WasteRecord.Collection status);
    List<WasteRecord> findByActiveTrueAndSegregationStatus(WasteRecord.Segregation status);
}
