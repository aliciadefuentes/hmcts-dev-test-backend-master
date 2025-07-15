package uk.gov.hmcts.reform.dev.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.dev.exceptions.TaskNotFoundException;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
class TaskServiceIntegrationTest {

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void setUp() {
        taskRepository.deleteAll();
    }

    @Test
    void createTask_WithValidData_ShouldCreateTaskSuccessfully() {
        String title = "Test Task";
        String description = "Test Description";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        Task createdTask = taskService.createTask(title, description, status, dueDate);

        assertNotNull(createdTask);
        assertNotEquals(0, createdTask.getId());
        assertNotNull(createdTask.getCaseNumber());
        assertEquals(title, createdTask.getTitle());
        assertEquals(description, createdTask.getDescription());
        assertEquals(status, createdTask.getStatus());
        assertEquals(dueDate, createdTask.getDueDate());
        assertNotNull(createdTask.getCreatedDate());

        Optional<Task> savedTask = taskRepository.findById(createdTask.getId());
        assertTrue(savedTask.isPresent());
        assertEquals(title, savedTask.get().getTitle());
    }

    @Test
    void createTask_WithNullStatus_ShouldDefaultToPending() {

        String title = "Test Task";
        String description = "Test Description";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        Task createdTask = taskService.createTask(title, description, null, dueDate);

        assertEquals("PENDING", createdTask.getStatus());
    }

    @Test
    void createTask_WithNullDueDate_ShouldSetDefaultDueDate() {

        String title = "Test Task";
        String description = "Test Description";
        String status = "PENDING";

        Task createdTask = taskService.createTask(title, description, status, null);

        assertNotNull(createdTask.getDueDate());
        assertTrue(createdTask.getDueDate().isAfter(LocalDateTime.now()));
    }

    @Test
    void createTask_WithNullTitle_ShouldThrowException() {

        String description = "Test Description";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.createTask(null, description, status, dueDate)
        );

        assertEquals("Title is required", exception.getMessage());
    }

    @Test
    void createTask_WithEmptyTitle_ShouldThrowException() {

        String title = "   ";
        String description = "Test Description";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.createTask(title, description, status, dueDate)
        );

        assertEquals("Title is required", exception.getMessage());
    }

    @Test
    void createTask_WithTitleTooLong_ShouldThrowException() {
        String title = "a".repeat(256);
        String description = "Test Description";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.createTask(title, description, status, dueDate)
        );

        assertEquals("Title must not exceed 255 characters", exception.getMessage());
    }

    @Test
    void createTask_WithInvalidStatus_ShouldThrowException() {

        String title = "Test Task";
        String description = "Test Description";
        String status = "INVALID_STATUS";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.createTask(title, description, status, dueDate)
        );

        assertTrue(exception.getMessage().contains("Invalid status"));
    }

    @Test
    void createTask_WithPastDueDate_ShouldThrowException() {

        String title = "Test Task";
        String description = "Test Description";
        String status = "PENDING";
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.createTask(title, description, status, pastDate)
        );

        assertEquals("Due date cannot be in the past", exception.getMessage());
    }

    @Test
    void createTask_ShouldGenerateUniqueCaseNumbers() {
        String title = "Test Task";
        String description = "Test Description";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        Task task1 = taskService.createTask(title + " 1", description, status, dueDate);
        Task task2 = taskService.createTask(title + " 2", description, status, dueDate);

        assertNotEquals(task1.getCaseNumber(), task2.getCaseNumber());
        assertTrue(task1.getCaseNumber().startsWith("TASK"));
        assertTrue(task2.getCaseNumber().startsWith("TASK"));
    }

    @Test
    void createTask_WithLowerCaseStatus_ShouldConvertToUpperCase() {
        String title = "Test Task";
        String description = "Test Description";
        String status = "pending";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        Task createdTask = taskService.createTask(title, description, status, dueDate);

        assertEquals("PENDING", createdTask.getStatus());
    }

    @Test
    void createTask_WithWhitespaceInFields_ShouldTrimFields() {
        String title = "  Test Task  ";
        String description = "  Test Description  ";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        Task createdTask = taskService.createTask(title, description, status, dueDate);

        assertEquals("Test Task", createdTask.getTitle());
        assertEquals("Test Description", createdTask.getDescription());
    }

    @Test
    void createTask_WithNullDescription_ShouldCreateSuccessfully() {
        String title = "Test Task";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        Task createdTask = taskService.createTask(title, null, status, dueDate);

        assertNotNull(createdTask);
        assertEquals(title, createdTask.getTitle());
        assertNull(createdTask.getDescription());
    }

    @Test
    void getAllTasks_WithNoTasks_ShouldReturnEmptyList() {
        List<Task> tasks = taskService.getAllTasks();

        assertTrue(tasks.isEmpty());
    }

    @Test
    void getAllTasks_WithMultipleTasks_ShouldReturnAllTasksOrderedByDueDate() {

        LocalDateTime date1 = LocalDateTime.now().plusDays(1);
        LocalDateTime date2 = LocalDateTime.now().plusDays(2);
        LocalDateTime date3 = LocalDateTime.now().plusDays(3);

        createTestTaskWithDueDate("Task 1", "PENDING", date1);
        createTestTaskWithDueDate("Task 2", "IN_PROGRESS", date2);
        createTestTaskWithDueDate("Task 3", "COMPLETED", date3);

        List<Task> tasks = taskService.getAllTasks();

        assertEquals(3, tasks.size());
        assertTrue(tasks.getFirst().getDueDate().isAfter(tasks.get(1).getDueDate())
                       || tasks.getFirst().getDueDate().isEqual(tasks.get(1).getDueDate()));
    }

    @Test
    void getTaskById_WithValidId_ShouldReturnTask() {

        Task savedTask = createTestTask("Test Task", "PENDING");

        Task retrievedTask = taskService.getTaskById(savedTask.getId());

        assertNotNull(retrievedTask);
        assertEquals(savedTask.getId(), retrievedTask.getId());
        assertEquals("Test Task", retrievedTask.getTitle());
        assertEquals("PENDING", retrievedTask.getStatus());
    }

    @Test
    void getTaskById_WithInvalidId_ShouldThrowException() {

        TaskNotFoundException exception = assertThrows(
            TaskNotFoundException.class,
            () -> taskService.getTaskById(999)
        );

        assertEquals("Task with ID 999 not found", exception.getMessage());
    }

    @Test
    void findTaskById_WithValidId_ShouldReturnOptionalWithTask() {

        Task savedTask = createTestTask("Test Task", "PENDING");

        Optional<Task> retrievedTask = taskService.findTaskById(savedTask.getId());

        assertTrue(retrievedTask.isPresent());
        assertEquals(savedTask.getId(), retrievedTask.get().getId());
    }

    @Test
    void findTaskById_WithInvalidId_ShouldReturnEmptyOptional() {

        Optional<Task> retrievedTask = taskService.findTaskById(999);

        assertTrue(retrievedTask.isEmpty());
    }

    @Test
    void updateTaskStatus_WithValidData_ShouldUpdateSuccessfully() {

        Task savedTask = createTestTask("Test Task", "PENDING");
        String newStatus = "IN_PROGRESS";

        Task updatedTask = taskService.updateTaskStatus(savedTask.getId(), newStatus);

        assertEquals(newStatus, updatedTask.getStatus());
        assertEquals(savedTask.getId(), updatedTask.getId());
        assertEquals(savedTask.getTitle(), updatedTask.getTitle());

        Task dbTask = taskRepository.findById(savedTask.getId()).orElseThrow();
        assertEquals(newStatus, dbTask.getStatus());
    }

    @Test
    void updateTaskStatus_WithInvalidId_ShouldThrowException() {

        TaskNotFoundException exception = assertThrows(
            TaskNotFoundException.class,
            () -> taskService.updateTaskStatus(999, "IN_PROGRESS")
        );

        assertEquals("Task with ID 999 not found", exception.getMessage());
    }

    @Test
    void updateTaskStatus_WithInvalidStatus_ShouldThrowException() {

        Task savedTask = createTestTask("Test Task", "PENDING");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.updateTaskStatus(savedTask.getId(), "INVALID_STATUS")
        );

        assertTrue(exception.getMessage().contains("Invalid status"));
    }

    @Test
    void updateTaskStatus_WithNullStatus_ShouldThrowException() {
        Task savedTask = createTestTask("Test Task", "PENDING");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.updateTaskStatus(savedTask.getId(), null)
        );

        assertEquals("Status cannot be empty", exception.getMessage());
    }

    @Test
    void updateTaskStatus_WithEmptyStatus_ShouldThrowException() {

        Task savedTask = createTestTask("Test Task", "PENDING");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.updateTaskStatus(savedTask.getId(), "   ")
        );

        assertEquals("Status cannot be empty", exception.getMessage());
    }

    @Test
    void updateTask_WithAllFields_ShouldUpdateSuccessfully() {

        LocalDateTime newDueDate = LocalDateTime.now().plusDays(10);
        String newTitle = "Updated Task";
        String newDescription = "Updated Description";
        String newStatus = "IN_PROGRESS";

        Task savedTask = createTestTask("Original Task", "PENDING");

        Task updatedTask = taskService.updateTask(savedTask.getId(), newTitle, newDescription, newStatus, newDueDate);

        assertEquals(newTitle, updatedTask.getTitle());
        assertEquals(newDescription, updatedTask.getDescription());
        assertEquals(newStatus, updatedTask.getStatus());
        assertEquals(newDueDate, updatedTask.getDueDate());

        Task dbTask = taskRepository.findById(savedTask.getId()).orElseThrow();
        assertEquals(newTitle, dbTask.getTitle());
        assertEquals(newDescription, dbTask.getDescription());
        assertEquals(newStatus, dbTask.getStatus());
    }

    @Test
    void updateTask_WithPartialFields_ShouldUpdateOnlyProvidedFields() {
        final Task savedTask = createTestTask("Original Task", "PENDING");

        Task updatedTask = taskService.updateTask(savedTask.getId(), "Updated Title", null, null, null);

        assertEquals("Updated Title", updatedTask.getTitle());
        assertEquals(savedTask.getDescription(), updatedTask.getDescription());
        assertEquals(savedTask.getStatus(), updatedTask.getStatus());
        assertEquals(savedTask.getDueDate(), updatedTask.getDueDate());
    }

    @Test
    void updateTask_WithEmptyTitle_ShouldNotUpdateTitle() {
        Task savedTask = createTestTask("Original Task", "PENDING");
        String originalTitle = savedTask.getTitle();

        Task updatedTask = taskService.updateTask(savedTask.getId(), "", "New Description", null, null);

        assertEquals(originalTitle, updatedTask.getTitle());
        assertEquals("New Description", updatedTask.getDescription());
    }

    @Test
    void updateTask_WithInvalidId_ShouldThrowException() {
        TaskNotFoundException exception = assertThrows(
            TaskNotFoundException.class,
            () -> taskService.updateTask(999, "New Title", null, null, null)
        );

        assertEquals("Task with ID 999 not found", exception.getMessage());
    }

    @Test
    void updateTask_WithInvalidStatus_ShouldThrowException() {
        Task savedTask = createTestTask("Test Task", "PENDING");

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> taskService.updateTask(savedTask.getId(), null, null, "INVALID_STATUS", null)
        );

        assertTrue(exception.getMessage().contains("Invalid status"));
    }

    @Test
    void updateTask_WithWhitespaceOnlyStatus_ShouldNotUpdateStatus() {
        Task savedTask = createTestTask("Test Task", "PENDING");
        String originalStatus = savedTask.getStatus();

        Task updatedTask = taskService.updateTask(savedTask.getId(), null, null, "   ", null);

        assertEquals(originalStatus, updatedTask.getStatus());
    }

    @Test
    void deleteTask_WithValidId_ShouldDeleteSuccessfully() {
        Task savedTask = createTestTask("Test Task", "PENDING");
        int taskId = savedTask.getId();

        taskService.deleteTask(taskId);

        assertFalse(taskRepository.existsById(taskId));
    }

    @Test
    void deleteTask_WithInvalidId_ShouldThrowException() {
        TaskNotFoundException exception = assertThrows(
            TaskNotFoundException.class,
            () -> taskService.deleteTask(999)
        );

        assertEquals("Task with ID 999 not found", exception.getMessage());
    }

    @Test
    void getTasksByStatus_ShouldReturnFilteredTasks() {
        createTestTask("Pending Task 1", "PENDING");
        createTestTask("In Progress Task", "IN_PROGRESS");
        createTestTask("Pending Task 2", "PENDING");

        List<Task> pendingTasks = taskService.getTasksByStatus("PENDING");

        assertEquals(2, pendingTasks.size());
        assertTrue(pendingTasks.stream().allMatch(task -> "PENDING".equals(task.getStatus())));
    }

    @Test
    void getTasksByStatus_WithCaseInsensitive_ShouldReturnFilteredTasks() {
        createTestTask("Pending Task", "PENDING");

        List<Task> pendingTasks = taskService.getTasksByStatus("pending");

        assertEquals(1, pendingTasks.size());
        assertEquals("PENDING", pendingTasks.getFirst().getStatus());
    }

    @Test
    void searchTasks_WithMatchingTitle_ShouldReturnFilteredTasks() {
        createTestTask("Important Task", "PENDING");
        createTestTask("Regular Task", "IN_PROGRESS");
        createTestTask("Another Important Item", "COMPLETED");

        List<Task> searchResults = taskService.searchTasks("Important");

        assertEquals(2, searchResults.size());
        assertTrue(searchResults.stream().anyMatch(task -> task.getTitle().contains("Important")));
    }

    @Test
    void searchTasks_WithEmptySearchTerm_ShouldReturnAllTasks() {
        createTestTask("Task 1", "PENDING");
        createTestTask("Task 2", "IN_PROGRESS");

        List<Task> searchResults = taskService.searchTasks("");

        assertEquals(2, searchResults.size());
    }

    @Test
    void searchTasks_WithNullSearchTerm_ShouldReturnAllTasks() {
        createTestTask("Task 1", "PENDING");
        createTestTask("Task 2", "IN_PROGRESS");

        List<Task> searchResults = taskService.searchTasks(null);

        assertEquals(2, searchResults.size());
    }

    @Test
    void searchTasks_WithPagination_ShouldReturnPagedResults() {
        for (int i = 1; i <= 10; i++) {
            createTestTask("Task " + i, "PENDING");
        }

        List<Task> firstPage = taskService.searchTasks(null, null, 0, 5);
        List<Task> secondPage = taskService.searchTasks(null, null, 5, 5);

        assertEquals(5, firstPage.size());
        assertEquals(5, secondPage.size());

        assertTrue(firstPage.stream().noneMatch(task ->
                                                    secondPage.stream().anyMatch(t -> t.getId() == task.getId())
        ));
    }

    @Test
    void searchTasks_WithStatusFilter_ShouldReturnFilteredResults() {
        createTestTask("Task 1", "PENDING");
        createTestTask("Task 2", "IN_PROGRESS");
        createTestTask("Task 3", "PENDING");

        List<Task> pendingTasks = taskService.searchTasks(null, "PENDING", 0, 10);

        assertEquals(2, pendingTasks.size());
        assertTrue(pendingTasks.stream().allMatch(task -> "PENDING".equals(task.getStatus())));
    }

    @Test
    void countAllTasks_ShouldReturnCorrectCount() {
        createTestTask("Task 1", "PENDING");
        createTestTask("Task 2", "IN_PROGRESS");
        createTestTask("Task 3", "COMPLETED");

        long count = taskService.countAllTasks();

        assertEquals(3, count);
    }

    @Test
    void countFilteredTasks_WithNoFilters_ShouldReturnTotalCount() {

        createTestTask("Task 1", "PENDING");
        createTestTask("Task 2", "IN_PROGRESS");

        long count = taskService.countFilteredTasks(null, null);

        assertEquals(2, count);
    }

    @Test
    void getOverdueTasks_ShouldReturnOnlyOverdueTasks() {

        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);

        createTestTaskWithDueDate("Overdue Task 1", "PENDING", pastDate);
        createTestTaskWithDueDate("Overdue Task 2", "IN_PROGRESS", pastDate);
        createTestTaskWithDueDate("Completed Overdue Task", "COMPLETED", pastDate);
        createTestTaskWithDueDate("Future Task", "PENDING", LocalDateTime.now().plusDays(1));

        List<Task> overdueTasks = taskService.getOverdueTasks();

        assertEquals(2, overdueTasks.size());
        assertTrue(overdueTasks.stream().allMatch(task ->
                                                      task.getDueDate().isBefore(LocalDateTime.now())
                                                          && !"COMPLETED".equals(task.getStatus())));
    }

    @Test
    void getTasksDueBefore_ShouldReturnTasksDueBeforeSpecifiedDate() {
        LocalDateTime cutoffDate = LocalDateTime.now().plusDays(5);
        LocalDateTime beforeDate = LocalDateTime.now().plusDays(3);
        LocalDateTime afterDate = LocalDateTime.now().plusDays(7);

        createTestTaskWithDueDate("Due Soon Task", "PENDING", beforeDate);
        createTestTaskWithDueDate("Due Later Task", "PENDING", afterDate);

        List<Task> tasksDueSoon = taskService.getTasksDueBefore(cutoffDate);

        assertEquals(1, tasksDueSoon.size());
        assertEquals("Due Soon Task", tasksDueSoon.getFirst().getTitle());
    }


    @Test
    void getTaskStatistics_ShouldReturnCorrectCounts() {
        createTestTask("Pending Task 1", "PENDING");
        createTestTask("Pending Task 2", "PENDING");
        createTestTask("In Progress Task", "IN_PROGRESS");
        createTestTask("Completed Task", "COMPLETED");

        Map<String, Long> statistics = taskService.getTaskStatistics();

        assertEquals(4L, statistics.get("total"));
        assertEquals(2L, statistics.get("pending"));
        assertEquals(1L, statistics.get("in_progress"));
        assertEquals(1L, statistics.get("completed"));
    }

    @Test
    void getTaskStatistics_WithNoTasks_ShouldReturnZeroCounts() {
        Map<String, Long> statistics = taskService.getTaskStatistics();

        assertEquals(0L, statistics.get("total"));
        assertEquals(0L, statistics.get("overdue"));
    }

    @Test
    void getTaskStatistics_WithOverdueTasks_ShouldIncludeOverdueCount() {
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        createTestTaskWithDueDate("Overdue Task", "PENDING", pastDate);
        createTestTask("Current Task", "PENDING");

        Map<String, Long> statistics = taskService.getTaskStatistics();

        assertEquals(2L, statistics.get("total"));
        assertEquals(1L, statistics.get("overdue"));
    }

    @Test
    void getTaskByCaseNumber_WithValidCaseNumber_ShouldReturnTask() {

        Task savedTask = createTestTask("Test Task", "PENDING");
        String caseNumber = savedTask.getCaseNumber();

        Task retrievedTask = taskService.getTaskByCaseNumber(caseNumber);

        assertNotNull(retrievedTask);
        assertEquals(caseNumber, retrievedTask.getCaseNumber());
        assertEquals("Test Task", retrievedTask.getTitle());
    }

    @Test
    void getTaskByCaseNumber_WithInvalidCaseNumber_ShouldThrowException() {
        TaskNotFoundException exception = assertThrows(
            TaskNotFoundException.class,
            () -> taskService.getTaskByCaseNumber("INVALID123")
        );

        assertEquals("Task with case number INVALID123 not found", exception.getMessage());
    }

    @Test
    void isValidStatus_WithValidStatuses_ShouldReturnTrue() {
        assertTrue(TaskService.Status.isValid("PENDING"));
        assertTrue(TaskService.Status.isValid("IN_PROGRESS"));
        assertTrue(TaskService.Status.isValid("COMPLETED"));
        assertTrue(TaskService.Status.isValid("CANCELLED"));
        assertTrue(TaskService.Status.isValid("ON_HOLD"));

        assertTrue(TaskService.Status.isValid("pending"));
        assertTrue(TaskService.Status.isValid("in_progress"));
    }

    @Test
    void isValidStatus_WithInvalidStatus_ShouldReturnFalse() {
        assertFalse(TaskService.Status.isValid("INVALID_STATUS"));
        assertFalse(TaskService.Status.isValid(""));
        assertFalse(TaskService.Status.isValid(null));
    }

    @Test
    void getValidStatuses_ShouldReturnAllValidStatuses() {
        List<String> validStatuses = TaskService.Status.getValidStatuses();

        assertEquals(5, validStatuses.size());
        assertTrue(validStatuses.contains("PENDING"));
        assertTrue(validStatuses.contains("IN_PROGRESS"));
        assertTrue(validStatuses.contains("COMPLETED"));
        assertTrue(validStatuses.contains("CANCELLED"));
        assertTrue(validStatuses.contains("ON_HOLD"));
    }

    @Test
    void createMultipleTasks_ShouldHaveUniqueCaseNumbers() {
        Task task1 = createTestTask("Task 1", "PENDING");
        Task task2 = createTestTask("Task 2", "PENDING");
        Task task3 = createTestTask("Task 3", "PENDING");

        assertNotEquals(task1.getCaseNumber(), task2.getCaseNumber());
        assertNotEquals(task2.getCaseNumber(), task3.getCaseNumber());
        assertNotEquals(task1.getCaseNumber(), task3.getCaseNumber());
    }

    @Test
    void createTask_WithExactly255CharacterTitle_ShouldSucceed() {
        String title = "a".repeat(255);
        String description = "Test Description";
        String status = "PENDING";
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);

        Task createdTask = taskService.createTask(title, description, status, dueDate);

        assertNotNull(createdTask);
        assertEquals(title, createdTask.getTitle());
    }

    private Task createTestTask(String title, String status) {
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        return createTestTaskWithDueDate(title, status, dueDate);
    }

    private Task createTestTaskWithDueDate(String title, String status, LocalDateTime dueDate) {
        Task task = taskService.createTask(title, "Test description for " + title, status,
                                           LocalDateTime.now().plusDays(4));
        return taskService.updateTask(task.getId(), null, null, null, dueDate);
    }
}
