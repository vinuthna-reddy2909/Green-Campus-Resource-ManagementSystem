package edu.campus.green;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<CampusLocation, String> {
    List<CampusLocation> findAllByOrderByNameAsc();
}
