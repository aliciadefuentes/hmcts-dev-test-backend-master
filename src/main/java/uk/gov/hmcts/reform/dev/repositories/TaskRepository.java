package uk.gov.hmcts.reform.dev.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.dev.models.Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Integer> {

    /**
     * Find tasks by status (case-insensitive).
     */
    List<Task> findByStatusIgnoreCaseOrderByCreatedDateDesc(String status);

    /**
     * Find tasks due before a specific date.
     */
    List<Task> findByDueDateBeforeOrderByDueDateAsc(LocalDateTime dueDate);

    /**
     * Find overdue tasks (due date passed and not completed).
     */
    @Query("SELECT t FROM Task t WHERE t.dueDate < :currentDate AND t.status != 'COMPLETED'")
    List<Task> findOverdueTasks(@Param("currentDate") LocalDateTime currentDate);

    /**
     * Search tasks by title, description, or case number.
     */
    @Query("SELECT t FROM Task t WHERE "
        + "LOWER(t.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR "
        + "LOWER(t.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR "
        + "LOWER(t.caseNumber) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Task> searchTasks(@Param("searchTerm") String searchTerm);

    /**
     * Find task by case number.
     */
    Optional<Task> findByCaseNumber(String caseNumber);

    /**
     * Get count of tasks by status.
     */
    @Query("SELECT t.status, COUNT(t) FROM Task t GROUP BY t.status")
    List<Object[]> getTaskCountByStatus();

    /**
     * Check if case number exists.
     */
    boolean existsByCaseNumber(String caseNumber);

    /**
     * Find tasks created between dates.
     */
    List<Task> findByCreatedDateBetweenOrderByCreatedDateDesc(
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    /**
     * Find all tasks ordered by due day.
     * */
    List<Task> findAllByOrderByDueDateDesc();

    @Query("SELECT t FROM Task t ORDER BY t.dueDate DESC LIMIT :pageSize OFFSET :offset")
    List<Task> findAllWithPagination(@Param("offset") int offset, @Param("pageSize") int pageSize);

    @Query(value = "SELECT * FROM tasks t WHERE "
        + "(:search IS NULL OR LOWER(t.case_number) LIKE LOWER(CONCAT('%', :search, '%')) OR "
        + "LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR "
        + "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) AND "
        + "(:status IS NULL OR LOWER(t.status) = LOWER(:status)) "
        + "ORDER BY t.due_date DESC "
        + "LIMIT :pageSize OFFSET :offset",
        nativeQuery = true)
    List<Task> searchTasksWithPagination(@Param("search") String search,
                                         @Param("status") String status,
                                         @Param("offset") int offset,
                                         @Param("pageSize") int pageSize);

    @Query("SELECT COUNT(t) FROM Task t WHERE "
        + "(:search IS NULL OR LOWER(t.caseNumber) LIKE LOWER(CONCAT('%', :search, '%')) OR "
        + "LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR "
        + "LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) AND "
        + "(:status IS NULL OR LOWER(t.status) = LOWER(:status))")
    long countFilteredTasks(@Param("search") String search, @Param("status") String status);
}
