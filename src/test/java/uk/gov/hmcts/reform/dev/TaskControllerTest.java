package uk.gov.hmcts.reform.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.dev.controllers.TaskController;
import uk.gov.hmcts.reform.dev.exceptions.TaskNotFoundException;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(TaskControllerTest.MockConfig.class)
class TaskControllerTest {

    public static final String API_TASKS_V1_PATH = "/api/v1/tasks";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ObjectMapper objectMapper;

    private Task sampleTask;
    private LocalDateTime dueDate;

    @BeforeEach
    void setUp() {
        Mockito.reset(taskService);

        dueDate = LocalDateTime.now().plusDays(1);
        sampleTask = new Task("TASK000001", "Test Task", "Test Description", "PENDING", dueDate);
        sampleTask.setId(1);
    }

    @Test
    void getTaskById_ShouldReturnTaskWhenExists() throws Exception {
        when(taskService.getTaskById(1)).thenReturn(sampleTask);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.title").value("Test Task"));

        verify(taskService).getTaskById(1);
    }

    @Test
    void getTaskById_ShouldReturnNotFoundWhenTaskNotExists() throws Exception {
        when(taskService.getTaskById(1)).thenThrow(new TaskNotFoundException("Task not found"));

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void createTask_ShouldCreateTaskSuccessfully() throws Exception {
        when(taskService.createTask(anyString(), anyString(), anyString(), any(LocalDateTime.class)))
            .thenReturn(sampleTask);

        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("Test Task");
        request.setDescription("Test Description");
        request.setStatus("PENDING");
        request.setDueDate(dueDate);

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Test Task"))
            .andExpect(jsonPath("$.description").value("Test Description"))
            .andExpect(jsonPath("$.status").value("PENDING"));

        verify(taskService).createTask("Test Task", "Test Description", "PENDING", dueDate);
    }

    @Test
    void createTask_ShouldReturnBadRequestWhenTitleMissing() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setDescription("Test Description");
        request.setDueDate(dueDate);

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_ShouldReturnBadRequestWhenDueDateMissing() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("Test Task");
        request.setDescription("Test Description");

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getAllTasks_ShouldReturnTasksWithPagination() throws Exception {
        List<Task> tasks = Collections.singletonList(sampleTask);
        when(taskService.searchTasks(null, null, 0, 10)).thenReturn(tasks);
        when(taskService.countFilteredTasks(null, null)).thenReturn(1L);

        mockMvc.perform(get(API_TASKS_V1_PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks[0].title").value("Test Task"))
            .andExpect(jsonPath("$.totalTasks").value(1))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    void getAllTasks_ShouldHandleSearchAndStatusFilters() throws Exception {
        List<Task> tasks = Collections.singletonList(sampleTask);
        when(taskService.searchTasks("test", "PENDING", 0, 10)).thenReturn(tasks);
        when(taskService.countFilteredTasks("test", "PENDING")).thenReturn(1L);

        mockMvc.perform(get(API_TASKS_V1_PATH)
                            .param("search", "test")
                            .param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.totalTasks").value(1));

        verify(taskService).searchTasks("test", "PENDING", 0, 10);
        verify(taskService).countFilteredTasks("test", "PENDING");
    }

    @Test
    void getAllTasks_ShouldHandlePaginationParameters() throws Exception {
        List<Task> tasks = Collections.singletonList(sampleTask);
        when(taskService.searchTasks(null, null, 10, 5)).thenReturn(tasks);
        when(taskService.countFilteredTasks(null, null)).thenReturn(15L);

        mockMvc.perform(get(API_TASKS_V1_PATH)
                            .param("page", "3")
                            .param("pageSize", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentPage").value(3))
            .andExpect(jsonPath("$.pageSize").value(5))
            .andExpect(jsonPath("$.totalPages").value(3));

        verify(taskService).searchTasks(null, null, 10, 5);
    }

    @Test
    void getAllTasks_ShouldValidatePaginationParameters() throws Exception {
        List<Task> tasks = Collections.singletonList(sampleTask);
        when(taskService.searchTasks(null, null, 0, 10)).thenReturn(tasks);
        when(taskService.countFilteredTasks(null, null)).thenReturn(1L);

        mockMvc.perform(get(API_TASKS_V1_PATH)
                            .param("page", "0")
                            .param("pageSize", "200"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.pageSize").value(10));

        verify(taskService).searchTasks(null, null, 0, 10);
    }

    @Test
    void updateTaskStatus_ShouldUpdateSuccessfully() throws Exception {
        when(taskService.updateTaskStatus(1, "COMPLETED")).thenReturn(sampleTask);

        TaskController.UpdateStatusRequest request = new TaskController.UpdateStatusRequest();
        request.setStatus("COMPLETED");

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));

        verify(taskService).updateTaskStatus(1, "COMPLETED");
    }

    @Test
    void updateTaskStatus_ShouldReturnBadRequestWhenStatusMissing() throws Exception {
        TaskController.UpdateStatusRequest request = new TaskController.UpdateStatusRequest();

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateTask_ShouldUpdateSuccessfully() throws Exception {
        when(taskService.updateTask(eq(1), anyString(), anyString(), anyString(), any(LocalDateTime.class)))
            .thenReturn(sampleTask);

        TaskController.UpdateTaskRequest request = new TaskController.UpdateTaskRequest();
        request.setTitle("Updated Task");
        request.setDescription("Updated Description");
        request.setStatus("COMPLETED");
        request.setDueDate(dueDate);

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Test Task"));

        verify(taskService).updateTask(1, "Updated Task", "Updated Description", "COMPLETED", dueDate);
    }

    @Test
    void deleteTask_ShouldDeleteSuccessfully() throws Exception {
        mockMvc.perform(delete(API_TASKS_V1_PATH +
                                   "/1"))
            .andExpect(status().isNoContent());

        verify(taskService).deleteTask(1);
    }

    @Test
    void deleteTask_ShouldReturnNotFoundWhenTaskNotExists() throws Exception {
        doThrow(new TaskNotFoundException("Task not found")).when(taskService).deleteTask(1);

        mockMvc.perform(delete(API_TASKS_V1_PATH +
                                   "/1"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getTasksByStatus_ShouldReturnTasksWithStatus() throws Exception {
        List<Task> tasks = Collections.singletonList(sampleTask);
        when(taskService.getTasksByStatus("PENDING")).thenReturn(tasks);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/status/PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getOverdueTasks_ShouldReturnOverdueTasks() throws Exception {
        List<Task> tasks = Collections.singletonList(sampleTask);
        when(taskService.getOverdueTasks()).thenReturn(tasks);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/overdue"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].title").value("Test Task"));
    }

    @Test
    @DisplayName("GET /statistics - should return task stats")
    void getTaskStatistics_ShouldReturnStatistics() throws Exception {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", 10L);
        stats.put("pending", 5L);
        stats.put("completed", 3L);
        stats.put("overdue", 2L);

        when(taskService.getTaskStatistics()).thenReturn(stats);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/statistics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(10))
            .andExpect(jsonPath("$.pending").value(5))
            .andExpect(jsonPath("$.completed").value(3))
            .andExpect(jsonPath("$.overdue").value(2));
    }

    @Test
    void getValidStatuses_ShouldReturnValidStatuses() throws Exception {
        List<String> statuses = Arrays.asList("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ON_HOLD");

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/statuses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0]").value("PENDING"))
            .andExpect(jsonPath("$[1]").value("IN_PROGRESS"))
            .andExpect(jsonPath("$[2]").value("COMPLETED"))
            .andExpect(jsonPath("$[3]").value("CANCELLED"))
            .andExpect(jsonPath("$[4]").value("ON_HOLD"));
    }

    @Test
    void getTaskStatuses_ShouldReturnValidStatuses() throws Exception {
        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/statuses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void createTask_ShouldReturnBadRequestWhenTitleTooLong() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("a".repeat(256));
        request.setDescription("Test Description");
        request.setDueDate(dueDate);

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_ShouldReturnBadRequestWhenDescriptionTooLong() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("Test Task");
        request.setDescription("a".repeat(1001));
        request.setDueDate(dueDate);

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateTask_ShouldReturnBadRequestWhenTitleTooLong() throws Exception {
        TaskController.UpdateTaskRequest request = new TaskController.UpdateTaskRequest();
        request.setTitle("a".repeat(256));

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateTask_ShouldReturnBadRequestWhenDescriptionTooLong() throws Exception {
        TaskController.UpdateTaskRequest request = new TaskController.UpdateTaskRequest();
        request.setDescription("a".repeat(1001));

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateTaskStatus_ShouldReturnNotFoundWhenTaskNotExists() throws Exception {
        when(taskService.updateTaskStatus(1, "COMPLETED"))
            .thenThrow(new TaskNotFoundException("Task not found"));

        TaskController.UpdateStatusRequest request = new TaskController.UpdateStatusRequest();
        request.setStatus("COMPLETED");

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/1/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNotFound());
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        @Primary
        public TaskService taskService() {
            return Mockito.mock(TaskService.class);
        }
    }
}
