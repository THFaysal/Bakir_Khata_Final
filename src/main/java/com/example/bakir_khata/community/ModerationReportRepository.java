package com.example.bakir_khata.community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.List;
public interface ModerationReportRepository extends JpaRepository<ModerationReport,Long> {
    @EntityGraph(attributePaths={"broadcast", "reporter"})
    List<ModerationReport> findByStatusOrderByCreatedAtDesc(ModerationReport.Status status);
}
