package uk.gov.hmcts.reform.dev;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.hmcts.reform.dev.exceptions.TaskNotFoundException;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    private Task sampleTask;
    private LocalDateTime dueDate;

    @BeforeEach
    void setUp() {
        dueDate = LocalDateTime.now().plusDays(1);
        sampleTask = new Task("TASK000001", "Test Task", "Test Description", "PENDING", dueDate);
        sampleTask.setId(1);
    }

    @Test
    void createTask_ShouldCreateTaskWithValidData() {
        when(taskRepository.existsByCaseNumber(anyString())).thenReturn(false);
        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask);

        Task result = taskService.createTask("Test Task", "Test Description", "PENDING", dueDate);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Test Task");
        assertThat(result.getDescription()).isEqualTo("Test Description");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void createTask_ShouldThrowExceptionWhenTitleIsEmpty() {
        assertThatThrownBy(() -> taskService.createTask("", "Description", "PENDING", dueDate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Title is required");
    }

    @Test
    void createTask_ShouldThrowExceptionWhenTitleTooLong() {
        String longTitle = "a".repeat(256);

        assertThatThrownBy(() -> taskService.createTask(longTitle, "Description", "PENDING", dueDate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Title must not exceed 255 characters");
    }

    @Test
    void createTask_ShouldThrowExceptionWhenInvalidStatus() {
        assertThatThrownBy(() -> taskService.createTask("Title", "Description", "INVALID", dueDate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid status");
    }

    @Test
    void createTask_ShouldThrowExceptionWhenDueDateInPast() {
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);

        assertThatThrownBy(() -> taskService.createTask("Title", "Description", "PENDING", pastDate))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Due date cannot be in the past");
    }

    @Test
    void createTask_ShouldUseDefaultsWhenOptionalParametersNull() {
        when(taskRepository.existsByCaseNumber(anyString())).thenReturn(false);
        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask);

        Task result = taskService.createTask("Test Task", null, null, null);

        assertThat(result).isNotNull();
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void getAllTasks_ShouldReturnAllTasks() {
        List<Task> tasks = Arrays.asList(sampleTask);
        when(taskRepository.findAllByOrderByDueDateDesc()).thenReturn(tasks);

        List<Task> result = taskService.getAllTasks();

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(sampleTask);
    }

    @Test
    void searchTasks_ShouldReturnFilteredTasks() {
        List<Task> tasks = Arrays.asList(sampleTask);
        when(taskRepository.searchTasksWithPagination("test", "PENDING", 0, 10)).thenReturn(tasks);

        List<Task> result = taskService.searchTasks("test", "PENDING", 0, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(sampleTask);
    }

    @Test
    void getTaskById_ShouldReturnTaskWhenExists() {
        when(taskRepository.findById(1)).thenReturn(Optional.of(sampleTask));

        Task result = taskService.getTaskById(1);

        assertThat(result).isEqualTo(sampleTask);
    }

    @Test
    void getTaskById_ShouldThrowExceptionWhenNotExists() {
        when(taskRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskById(1))
            .isInstanceOf(TaskNotFoundException.class)
            .hasMessage("Task with ID 1 not found");
    }

    @Test
    void updateTaskStatus_ShouldUpdateStatus() {
        when(taskRepository.findById(1)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask);

        Task result = taskService.updateTaskStatus(1, "COMPLETED");

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        verify(taskRepository).save(sampleTask);
    }

    @Test
    void updateTaskStatus_ShouldThrowExceptionWhenInvalidStatus() {
        assertThatThrownBy(() -> taskService.updateTaskStatus(1, "INVALID"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid status");

    }

    @Test
    void updateTask_ShouldUpdateAllFields() {
        when(taskRepository.findById(1)).thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(Task.class))).thenReturn(sampleTask);

        LocalDateTime newDueDate = LocalDateTime.now().plusDays(2);
        Task result = taskService.updateTask(1, "New Title", "New Description", "COMPLETED", newDueDate);

        assertThat(result.getTitle()).isEqualTo("New Title");
        assertThat(result.getDescription()).isEqualTo("New Description");
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getDueDate()).isEqualTo(newDueDate);
    }

    @Test
    void deleteTask_ShouldDeleteWhenExists() {
        when(taskRepository.existsById(1)).thenReturn(true);

        taskService.deleteTask(1);

        verify(taskRepository).deleteById(1);
    }

    @Test
    void deleteTask_ShouldThrowExceptionWhenNotExists() {
        when(taskRepository.existsById(1)).thenReturn(false);

        assertThatThrownBy(() -> taskService.deleteTask(1))
            .isInstanceOf(TaskNotFoundException.class)
            .hasMessage("Task with ID 1 not found");
    }

    @Test
    void getTasksByStatus_ShouldReturnTasksWithStatus() {
        List<Task> tasks = Arrays.asList(sampleTask);
        when(taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc("PENDING")).thenReturn(tasks);

        List<Task> result = taskService.getTasksByStatus("PENDING");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void getOverdueTasks_ShouldReturnOverdueTasks() {
        List<Task> tasks = Arrays.asList(sampleTask);
        when(taskRepository.findOverdueTasks(any(LocalDateTime.class))).thenReturn(tasks);

        List<Task> result = taskService.getOverdueTasks();

        assertThat(result).hasSize(1);
        verify(taskRepository).findOverdueTasks(any(LocalDateTime.class));
    }

    @Test
    void getTaskStatistics_ShouldReturnStatistics() {
        when(taskRepository.count()).thenReturn(3L);
        when(taskRepository.getTaskCountByStatus()).thenReturn(Arrays.asList(
            new Object[]{"PENDING", 1L},
            new Object[]{"COMPLETED", 2L}
        ));
        when(taskRepository.findOverdueTasks(any(LocalDateTime.class))).thenReturn(Arrays.asList(sampleTask));

        Map<String, Long> result = taskService.getTaskStatistics();

        assertThat(result.get("total")).isEqualTo(3L);
        assertThat(result.get("pending")).isEqualTo(1L);
        assertThat(result.get("completed")).isEqualTo(2L);
        assertThat(result.get("overdue")).isEqualTo(1L);
    }

    @Test
    void getTaskByCaseNumber_ShouldReturnTaskWhenExists() {
        when(taskRepository.findByCaseNumber("TASK000001")).thenReturn(Optional.of(sampleTask));

        Task result = taskService.getTaskByCaseNumber("TASK000001");

        assertThat(result).isEqualTo(sampleTask);
    }

    @Test
    void getTaskByCaseNumber_ShouldThrowExceptionWhenNotExists() {
        when(taskRepository.findByCaseNumber("NONEXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTaskByCaseNumber("NONEXISTENT"))
            .isInstanceOf(TaskNotFoundException.class)
            .hasMessage("Task with case number NONEXISTENT not found");
    }

    @Test
    void countFilteredTasks_ShouldReturnCount() {
        when(taskRepository.countFilteredTasks("search", "PENDING")).thenReturn(5L);

        long result = taskService.countFilteredTasks("search", "PENDING");

        assertThat(result).isEqualTo(5L);
    }

    @Test
    void countFilteredTasks_ShouldReturnTotalCountWhenNoFilters() {
        when(taskRepository.count()).thenReturn(10L);

        long result = taskService.countFilteredTasks("", "");

        assertThat(result).isEqualTo(10L);
    }

    @Test
    void statusEnumIsValid_ShouldReturnTrueForValidStatus() {
        assertThat(TaskService.Status.isValid("PENDING")).isTrue();
        assertThat(TaskService.Status.isValid("pending")).isTrue();
        assertThat(TaskService.Status.isValid("COMPLETED")).isTrue();
    }

    @Test
    void statusEnumIsValid_ShouldReturnFalseForInvalidStatus() {
        assertThat(TaskService.Status.isValid("INVALID")).isFalse();
        assertThat(TaskService.Status.isValid(null)).isFalse();
        assertThat(TaskService.Status.isValid("")).isFalse();
    }

    @Test
    void statusEnumGetValidStatuses_ShouldReturnAllStatuses() {
        List<String> statuses = TaskService.Status.getValidStatuses();

        assertThat(statuses).contains("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ON_HOLD");
        assertThat(statuses).hasSize(5);
    }
}
