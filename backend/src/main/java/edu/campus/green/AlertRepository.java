package edu.campus.green;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<Alert, String> {
    List<Alert> findAllByOrderByCreatedAtDesc();
    List<Alert> findByResolvedFalseOrderByCreatedAtDesc();
}
