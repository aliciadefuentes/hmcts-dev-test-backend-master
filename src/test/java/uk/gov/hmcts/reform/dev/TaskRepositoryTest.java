package uk.gov.hmcts.reform.dev;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TaskRepository taskRepository;

    private Task task1;
    private Task task2;
    private Task task3;

    @BeforeEach
    void setUp() {
        task1 = new Task("TASK000001", "Test Task 1", "Description 1", "PENDING",
                         LocalDateTime.now().plusDays(1));
        task2 = new Task("TASK000002", "Test Task 2", "Description 2", "COMPLETED",
                         LocalDateTime.now().minusDays(1));
        task3 = new Task("TASK000003", "Test Task 3", "Description 3", "IN_PROGRESS",
                         LocalDateTime.now().plusDays(2));

        entityManager.persistAndFlush(task1);
        entityManager.persistAndFlush(task2);
        entityManager.persistAndFlush(task3);
    }

    @Test
    void findByStatusIgnoreCaseOrderByCreatedDateDesc_ShouldReturnTasksWithMatchingStatus() {
        List<Task> pendingTasks = taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc("PENDING");

        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void findByStatusIgnoreCaseOrderByCreatedDateDesc_ShouldBeCaseInsensitive() {
        List<Task> pendingTasks = taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc("pending");

        assertThat(pendingTasks).hasSize(1);
        assertThat(pendingTasks.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void findByDueDateBeforeOrderByDueDateAsc_ShouldReturnOverdueTasks() {
        List<Task> overdueTasks = taskRepository.findByDueDateBeforeOrderByDueDateAsc(LocalDateTime.now());

        assertThat(overdueTasks).hasSize(1);
        assertThat(overdueTasks.get(0).getCaseNumber()).isEqualTo("TASK000002");
    }

    @Test
    void findOverdueTasks_ShouldReturnTasksOverdueAndNotCompleted() {
        Task overdueTask = new Task("TASK000004", "Overdue Task", "Overdue Description", "PENDING",
                                    LocalDateTime.now().minusDays(2));
        entityManager.persistAndFlush(overdueTask);

        List<Task> overdueTasks = taskRepository.findOverdueTasks(LocalDateTime.now());

        assertThat(overdueTasks).hasSize(1);
        assertThat(overdueTasks.get(0).getCaseNumber()).isEqualTo("TASK000004");
    }

    @Test
    void searchTasks_ShouldFindTasksByTitle() {
        List<Task> foundTasks = taskRepository.searchTasks("Test Task 1");

        assertThat(foundTasks).hasSize(1);
        assertThat(foundTasks.get(0).getTitle()).isEqualTo("Test Task 1");
    }

    @Test
    void searchTasks_ShouldFindTasksByDescription() {
        List<Task> foundTasks = taskRepository.searchTasks("Description 2");

        assertThat(foundTasks).hasSize(1);
        assertThat(foundTasks.get(0).getDescription()).isEqualTo("Description 2");
    }

    @Test
    void searchTasks_ShouldFindTasksByCaseNumber() {
        List<Task> foundTasks = taskRepository.searchTasks("TASK000001");

        assertThat(foundTasks).hasSize(1);
        assertThat(foundTasks.get(0).getCaseNumber()).isEqualTo("TASK000001");
    }

    @Test
    void findByCaseNumber_ShouldReturnTaskWhenExists() {
        Optional<Task> foundTask = taskRepository.findByCaseNumber("TASK000001");

        assertThat(foundTask).isPresent();
        assertThat(foundTask.get().getTitle()).isEqualTo("Test Task 1");
    }

    @Test
    void findByCaseNumber_ShouldReturnEmptyWhenNotExists() {
        Optional<Task> foundTask = taskRepository.findByCaseNumber("NONEXISTENT");

        assertThat(foundTask).isEmpty();
    }

    @Test
    void getTaskCountByStatus_ShouldReturnCorrectCounts() {
        List<Object[]> statusCounts = taskRepository.getTaskCountByStatus();

        assertThat(statusCounts).hasSize(3);
        assertThat(statusCounts).anyMatch(result ->
                                              "PENDING".equals(result[0]) && Long.valueOf(1).equals(result[1]));
    }

    @Test
    void existsByCaseNumber_ShouldReturnTrueWhenExists() {
        boolean exists = taskRepository.existsByCaseNumber("TASK000001");

        assertThat(exists).isTrue();
    }

    @Test
    void existsByCaseNumber_ShouldReturnFalseWhenNotExists() {
        boolean exists = taskRepository.existsByCaseNumber("NONEXISTENT");

        assertThat(exists).isFalse();
    }

    @Test
    void searchTasksWithPagination_ShouldReturnFilteredResults() {
        List<Task> results = taskRepository.searchTasksWithPagination("Test", null, 0, 10);

        assertThat(results).hasSize(3);
    }

    @Test
    void searchTasksWithPagination_ShouldFilterByStatus() {
        List<Task> results = taskRepository.searchTasksWithPagination(null, "PENDING", 0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void countFilteredTasks_ShouldReturnCorrectCount() {
        long count = taskRepository.countFilteredTasks("Test", null);

        assertThat(count).isEqualTo(3);
    }

    @Test
    void countFilteredTasks_ShouldFilterByStatus() {
        long count = taskRepository.countFilteredTasks(null, "PENDING");

        assertThat(count).isEqualTo(1);
    }
}
