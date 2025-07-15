package uk.gov.hmcts.reform.dev.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("TaskRepository Integration Tests")
class TaskRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TaskRepository taskRepository;

    private LocalDateTime now;
    private LocalDateTime futureDate;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
        LocalDateTime pastDate = now.minusDays(5);
        futureDate = now.plusDays(5);

        Task pendingTask = new Task(
            "TASK000001",
            "Pending Task",
            "This is a pending task",
            TaskService.Status.PENDING.name(),
            futureDate
        );

        Task completedTask = new Task(
            "TASK000002",
            "Completed Task",
            "This is a completed task",
            TaskService.Status.COMPLETED.name(),
            pastDate
        );

        Task overdueTask = new Task(
            "TASK000003",
            "Overdue Task",
            "This is an overdue task",
            TaskService.Status.PENDING.name(),
            pastDate.minusDays(1)
        );

        Task inProgressTask = new Task(
            "TASK000004",
            "In Progress Task",
            "This is an in progress task",
            TaskService.Status.IN_PROGRESS.name(),
            futureDate.plusDays(1)
        );

        entityManager.persistAndFlush(pendingTask);
        entityManager.persistAndFlush(completedTask);
        entityManager.persistAndFlush(overdueTask);
        entityManager.persistAndFlush(inProgressTask);
    }

    @Test
    @DisplayName("Should find tasks by status ignoring case and order by created date desc")
    void testFindByStatusIgnoreCaseOrderByCreatedDateDesc() {

        List<Task> pendingTasks = taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc(
            TaskService.Status.PENDING.name());
        assertThat(pendingTasks).hasSize(2);
        assertThat(pendingTasks).extracting(Task::getStatus).containsOnly(TaskService.Status.PENDING.name());

        List<Task> pendingTasksLowerCase = taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc("pending");
        assertThat(pendingTasksLowerCase).hasSize(2);
        assertThat(pendingTasksLowerCase).extracting(Task::getStatus).containsOnly(
            TaskService.Status.PENDING.name());

        List<Task> completedTasks = taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc("completed");
        assertThat(completedTasks).hasSize(1);
        assertThat(completedTasks.getFirst().getStatus()).isEqualTo(TaskService.Status.COMPLETED.name());

        List<Task> cancelledTasks = taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc(
            TaskService.Status.CANCELLED.name());
        assertThat(cancelledTasks).isEmpty();
    }

    @Test
    @DisplayName("Should find tasks due before specific date ordered by due date asc")
    void testFindByDueDateBeforeOrderByDueDateAsc() {
        List<Task> tasksDueBefore = taskRepository.findByDueDateBeforeOrderByDueDateAsc(now);

        assertThat(tasksDueBefore).hasSize(2);
        assertThat(tasksDueBefore).extracting(Task::getCaseNumber)
            .containsExactly("TASK000003", "TASK000002");

        for (int i = 0; i < tasksDueBefore.size() - 1; i++) {
            assertThat(tasksDueBefore.get(i).getDueDate())
                .isBeforeOrEqualTo(tasksDueBefore.get(i + 1).getDueDate());
        }
    }

    @Test
    @DisplayName("Should find overdue tasks (due date passed and not completed)")
    void testFindOverdueTasks() {
        List<Task> overdueTasks = taskRepository.findOverdueTasks(now);

        assertThat(overdueTasks).hasSize(1);
        assertThat(overdueTasks.getFirst().getCaseNumber()).isEqualTo("TASK000003");
        assertThat(overdueTasks.getFirst().getDueDate()).isBefore(now);
        assertThat(overdueTasks.getFirst().getStatus()).isNotEqualTo(TaskService.Status.COMPLETED.name());
    }

    @Test
    @DisplayName("Should search tasks by title, description, or case number")
    void testSearchTasks() {
        // Search by title
        List<Task> titleSearch = taskRepository.searchTasks("pending");
        assertThat(titleSearch).hasSize(1);
        assertThat(titleSearch.getFirst().getTitle()).containsIgnoringCase("pending");

        // Search by description
        List<Task> descriptionSearch = taskRepository.searchTasks("completed task");
        assertThat(descriptionSearch).hasSize(1);
        assertThat(descriptionSearch.getFirst().getDescription()).containsIgnoringCase("completed task");

        // Search by case number
        List<Task> caseNumberSearch = taskRepository.searchTasks("TASK000001");
        assertThat(caseNumberSearch).hasSize(1);
        assertThat(caseNumberSearch.getFirst().getCaseNumber()).isEqualTo("TASK000001");

        // Search with partial match
        List<Task> partialSearch = taskRepository.searchTasks("task");
        assertThat(partialSearch).hasSize(4); // All tasks have "task" in title or description

        // Search with no results
        List<Task> noResultsSearch = taskRepository.searchTasks("nonexistent");
        assertThat(noResultsSearch).isEmpty();

        List<Task> caseInsensitiveSearch = taskRepository.searchTasks("PENDING");
        assertThat(caseInsensitiveSearch).hasSize(1);
    }

    @Test
    @DisplayName("Should find task by case number")
    void testFindByCaseNumber() {
        Optional<Task> foundTask = taskRepository.findByCaseNumber("TASK000001");

        assertThat(foundTask).isPresent();
        assertThat(foundTask.get().getTitle()).isEqualTo("Pending Task");

        Optional<Task> notFoundTask = taskRepository.findByCaseNumber("TASK999999");
        assertThat(notFoundTask).isEmpty();
    }

    @Test
    @DisplayName("Should get count of tasks by status")
    void testGetTaskCountByStatus() {
        List<Object[]> statusCounts = taskRepository.getTaskCountByStatus();

        assertThat(statusCounts).hasSize(3); // PENDING, COMPLETED, IN_PROGRESS

        java.util.Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] result : statusCounts) {
            countMap.put((String) result[0], (Long) result[1]);
        }

        assertThat(countMap.get(TaskService.Status.PENDING.name())).isEqualTo(2L);
        assertThat(countMap.get(TaskService.Status.COMPLETED.name())).isEqualTo(1L);
        assertThat(countMap.get(TaskService.Status.IN_PROGRESS.name())).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should check if case number exists")
    void testExistsByCaseNumber() {
        assertThat(taskRepository.existsByCaseNumber("TASK000001")).isTrue();
        assertThat(taskRepository.existsByCaseNumber("TASK000002")).isTrue();
        assertThat(taskRepository.existsByCaseNumber("TASK999999")).isFalse();
        assertThat(taskRepository.existsByCaseNumber(null)).isFalse();
    }

    @Test
    @DisplayName("Should find tasks created between dates ordered by created date desc")
    void testFindByCreatedDateBetweenOrderByCreatedDateDesc() {
        LocalDateTime startDate = now.minusHours(1);
        LocalDateTime endDate = now.plusHours(1);

        List<Task> tasksInRange = taskRepository.findByCreatedDateBetweenOrderByCreatedDateDesc(startDate, endDate);

        assertThat(tasksInRange).hasSize(4);

        for (int i = 0; i < tasksInRange.size() - 1; i++) {
            assertThat(tasksInRange.get(i).getCreatedDate())
                .isAfterOrEqualTo(tasksInRange.get(i + 1).getCreatedDate());
        }

        LocalDateTime veryRecentStart = now.minusMinutes(1);
        LocalDateTime veryRecentEnd = now.plusMinutes(1);
        List<Task> recentTasks = taskRepository.findByCreatedDateBetweenOrderByCreatedDateDesc(veryRecentStart,
                                                                                               veryRecentEnd);
        assertThat(recentTasks).hasSize(4);

        LocalDateTime pastStart = now.minusDays(10);
        LocalDateTime pastEnd = now.minusDays(9);
        List<Task> pastTasks = taskRepository.findByCreatedDateBetweenOrderByCreatedDateDesc(pastStart, pastEnd);
        assertThat(pastTasks).isEmpty();
    }

    @Test
    @DisplayName("Should find all tasks ordered by due date desc")
    void testFindAllByOrderByDueDateDesc() {
        List<Task> allTasks = taskRepository.findAllByOrderByDueDateDesc();

        assertThat(allTasks).hasSize(4);

        for (int i = 0; i < allTasks.size() - 1; i++) {
            assertThat(allTasks.get(i).getDueDate())
                .isAfterOrEqualTo(allTasks.get(i + 1).getDueDate());
        }
    }

    @Test
    @DisplayName("Should handle edge cases for search functionality")
    void testSearchTasksEdgeCases() {
        // Empty search term
        List<Task> emptySearch = taskRepository.searchTasks("");
        assertThat(emptySearch).hasSize(4);

        // Whitespace only
        List<Task> whitespaceSearch = taskRepository.searchTasks("   ");
        assertThat(whitespaceSearch).isEmpty();

        // Special characters
        List<Task> specialCharSearch = taskRepository.searchTasks("@#$%");
        assertThat(specialCharSearch).isEmpty();

        // Very long search term
        String longSearchTerm = "a".repeat(1000);
        List<Task> longSearch = taskRepository.searchTasks(longSearchTerm);
        assertThat(longSearch).isEmpty();
    }

    @Test
    @DisplayName("Should handle case sensitivity correctly")
    void testCaseSensitivityHandling() {
        Task mixedCaseTask = new Task(
            "TASK000005",
            "MiXeD CaSe TiTlE",
            "MiXeD cAsE dEsCrIpTiOn",
            TaskService.Status.PENDING.name(),
            futureDate
        );
        entityManager.persistAndFlush(mixedCaseTask);

        List<Task> upperCaseSearch = taskRepository.searchTasks("MIXED");
        List<Task> lowerCaseSearch = taskRepository.searchTasks("mixed");
        List<Task> mixedCaseSearch = taskRepository.searchTasks("MiXeD");

        assertThat(upperCaseSearch).hasSize(1);
        assertThat(lowerCaseSearch).hasSize(1);
        assertThat(mixedCaseSearch).hasSize(1);

        assertThat(upperCaseSearch.getFirst().getId()).isEqualTo(mixedCaseTask.getId());
        assertThat(lowerCaseSearch.getFirst().getId()).isEqualTo(mixedCaseTask.getId());
        assertThat(mixedCaseSearch.getFirst().getId()).isEqualTo(mixedCaseTask.getId());
    }

    @Test
    @DisplayName("Should handle boundary conditions for due date queries")
    void testDueDateBoundaryConditions() {
        Task exactTimeTask = new Task(
            "TASK000006",
            "Exact Time Task",
            "Task with exact current time",
            TaskService.Status.PENDING.name(),
            now
        );
        entityManager.persistAndFlush(exactTimeTask);

        List<Task> exactBoundaryTasks = taskRepository.findByDueDateBeforeOrderByDueDateAsc(now);
        assertThat(exactBoundaryTasks).hasSize(2);

        List<Task> exactBoundaryOverdue = taskRepository.findOverdueTasks(now);
        assertThat(exactBoundaryOverdue).hasSize(1);
    }

    @Test
    @DisplayName("Should maintain data integrity with multiple operations")
    void testDataIntegrityWithMultipleOperations() {
        assertThat(taskRepository.count()).isEqualTo(4);

        Task newTask = new Task(
            "TASK000007",
            "New Task",
            "Newly added task",
            TaskService.Status.ON_HOLD.name(),
            futureDate
        );
        final Task savedTask = taskRepository.save(newTask);
        assertThat(taskRepository.count()).isEqualTo(5);

        assertThat(taskRepository.findByCaseNumber("TASK000007")).isPresent();
        assertThat(taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc(TaskService.Status.ON_HOLD.name()))
            .hasSize(1);
        assertThat(taskRepository.searchTasks("New Task")).hasSize(1);

        savedTask.setStatus(TaskService.Status.CANCELLED.name());
        taskRepository.save(savedTask);

        List<Object[]> updatedCounts = taskRepository.getTaskCountByStatus();
        java.util.Map<String, Long> countMap = new java.util.HashMap<>();
        for (Object[] result : updatedCounts) {
            countMap.put((String) result[0], (Long) result[1]);
        }
        assertThat(countMap.get(TaskService.Status.CANCELLED.name())).isEqualTo(1L);
        assertThat(countMap.get(TaskService.Status.ON_HOLD.name())).isNull();

        taskRepository.delete(savedTask);
        assertThat(taskRepository.count()).isEqualTo(4);
        assertThat(taskRepository.findByCaseNumber("TASK000007")).isEmpty();
    }

    @Test
    @DisplayName("Should find all tasks with pagination")
    void testFindAllWithPagination() {
        List<Task> firstPage = taskRepository.findAllWithPagination(0, 2);
        assertThat(firstPage).hasSize(2);

        List<Task> secondPage = taskRepository.findAllWithPagination(2, 2);
        assertThat(secondPage).hasSize(2);

        List<Task> beyondPage = taskRepository.findAllWithPagination(10, 2);
        assertThat(beyondPage).isEmpty();

        List<Task> largePage = taskRepository.findAllWithPagination(0, 10);
        assertThat(largePage).hasSize(4);

        for (int i = 0; i < largePage.size() - 1; i++) {
            assertThat(largePage.get(i).getDueDate())
                .isAfterOrEqualTo(largePage.get(i + 1).getDueDate());
        }
    }

    @Test
    @DisplayName("Should search tasks with pagination and filtering")
    void testSearchTasksWithPagination() {
        List<Task> allTasks = taskRepository.searchTasksWithPagination(null, null, 0, 10);
        assertThat(allTasks).hasSize(4);

        List<Task> searchResults = taskRepository.searchTasksWithPagination("task", null, 0, 10);
        assertThat(searchResults).hasSize(4);

        List<Task> pendingTasks = taskRepository.searchTasksWithPagination(null,
                                                                           TaskService.Status.PENDING.name(), 0, 10);
        assertThat(pendingTasks).hasSize(2);

        List<Task> filteredTasks = taskRepository.searchTasksWithPagination("pending",
                                                                            TaskService.Status.PENDING.name(), 0, 10);
        assertThat(filteredTasks).hasSize(1);

        List<Task> firstPage = taskRepository.searchTasksWithPagination("task", null, 0, 2);
        assertThat(firstPage).hasSize(2);

        List<Task> secondPage = taskRepository.searchTasksWithPagination("task", null, 2, 2);
        assertThat(secondPage).hasSize(2);

        List<Task> caseInsensitiveSearch = taskRepository.searchTasksWithPagination("TASK",
                                                                                    "pending", 0, 10);
        assertThat(caseInsensitiveSearch).hasSize(2);

        List<Task> noResults = taskRepository.searchTasksWithPagination("nonexistent", null, 0, 10);
        assertThat(noResults).isEmpty();

        List<Task> noStatusResults = taskRepository.searchTasksWithPagination(null, "NONEXISTENT", 0, 10);
        assertThat(noStatusResults).isEmpty();
    }

    @Test
    @DisplayName("Should count filtered tasks correctly")
    void testCountFilteredTasks() {
        long totalCount = taskRepository.countFilteredTasks(null, null);
        assertThat(totalCount).isEqualTo(4);

        long searchCount = taskRepository.countFilteredTasks("task", null);
        assertThat(searchCount).isEqualTo(4);

        long pendingCount = taskRepository.countFilteredTasks(null, TaskService.Status.PENDING.name());
        assertThat(pendingCount).isEqualTo(2);

        long filteredCount = taskRepository.countFilteredTasks("pending", TaskService.Status.PENDING.name());
        assertThat(filteredCount).isEqualTo(1);

        long caseInsensitiveCount = taskRepository.countFilteredTasks("TASK", "pending");
        assertThat(caseInsensitiveCount).isEqualTo(2L);

        long noResultsCount = taskRepository.countFilteredTasks("nonexistent", null);
        assertThat(noResultsCount).isEqualTo(0);

        long noStatusCount = taskRepository.countFilteredTasks(null, "NONEXISTENT");
        assertThat(noStatusCount).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle null and empty parameters in search methods")
    void testSearchMethodsWithNullAndEmptyParams() {
        List<Task> nullSearch = taskRepository.searchTasks(null);
        assertThat(nullSearch).hasSize(0);

        List<Task> emptySearchTermSearch = taskRepository.searchTasks("");
        assertThat(emptySearchTermSearch).hasSize(4);

        List<Task> nullSearchPagination = taskRepository.searchTasksWithPagination(null, null, 0, 10);
        assertThat(nullSearchPagination).hasSize(4);

        long nullCount = taskRepository.countFilteredTasks(null, null);
        assertThat(nullCount).isEqualTo(4);

        List<Task> emptySearch = taskRepository.searchTasksWithPagination("", null, 0, 10);
        assertThat(emptySearch).hasSize(4);

        long emptyCount = taskRepository.countFilteredTasks("", null);
        assertThat(emptyCount).isEqualTo(4);
    }

    @Test
    @DisplayName("Should maintain consistency between search and count methods")
    void testSearchAndCountConsistency() {
        String[] searchTerms = {null, "", "task", "pending", "TASK000001", "nonexistent"};
        String[] statuses = {null, TaskService.Status.PENDING.name(), TaskService.Status.COMPLETED.name(),
            "NONEXISTENT", "pending"};

        for (String searchTerm : searchTerms) {
            for (String status : statuses) {
                List<Task> searchResults = taskRepository.searchTasksWithPagination(searchTerm, status, 0, 100);
                long countResult = taskRepository.countFilteredTasks(searchTerm, status);

                assertThat(searchResults).hasSize((int) countResult)
                    .withFailMessage("Search results count (%d) doesn't match count method result "
                                         + "(%d) for search='%s', status='%s'", searchResults.size(), countResult,
                                     searchTerm, status);
            }
        }
    }

    @Test
    @DisplayName("Should handle edge cases for pagination parameters")
    void testPaginationEdgeCases() {
        List<Task> zeroOffset = taskRepository.findAllWithPagination(0, 2);
        assertThat(zeroOffset).hasSize(2);

        List<Task> zeroPageSize = taskRepository.findAllWithPagination(0, 0);
        assertThat(zeroPageSize).isEmpty();

        List<Task> largePageSize = taskRepository.findAllWithPagination(0, 1000);
        assertThat(largePageSize).hasSize(4);

        List<Task> largeOffset = taskRepository.findAllWithPagination(100, 10);
        assertThat(largeOffset).isEmpty();
    }

    @Test
    @DisplayName("Should verify ordering in paginated results")
    void testPaginatedResultsOrdering() {
        List<Task> firstPage = taskRepository.findAllWithPagination(0, 2);
        List<Task> secondPage = taskRepository.findAllWithPagination(2, 2);

        List<Task> allTasks = taskRepository.findAllWithPagination(0, 10);

        assertThat(firstPage.getFirst().getId()).isEqualTo(allTasks.getFirst().getId());
        assertThat(firstPage.get(1).getId()).isEqualTo(allTasks.get(1).getId());

        assertThat(secondPage.getFirst().getId()).isEqualTo(allTasks.get(2).getId());
        assertThat(secondPage.get(1).getId()).isEqualTo(allTasks.get(3).getId());

        List<Task> searchFirstPage = taskRepository.searchTasksWithPagination(null, null, 0, 2);
        List<Task> searchSecondPage = taskRepository.searchTasksWithPagination(null, null, 2, 2);

        for (int i = 0; i < searchFirstPage.size() - 1; i++) {
            assertThat(searchFirstPage.get(i).getDueDate())
                .isAfterOrEqualTo(searchFirstPage.get(i + 1).getDueDate());
        }

        if (!searchFirstPage.isEmpty() && !searchSecondPage.isEmpty()) {
            assertThat(searchFirstPage.getLast().getDueDate())
                .isAfterOrEqualTo(searchSecondPage.getFirst().getDueDate());
        }
    }
}
