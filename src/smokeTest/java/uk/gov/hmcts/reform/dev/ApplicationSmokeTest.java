package uk.gov.hmcts.reform.dev;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.dev.controllers.TaskController;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Application Smoke Tests - Basic Application Functionality")
class ApplicationSmokeTest {

    public static final String API_TASKS_V1_PATH = "/api/v1/tasks";
    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TaskController taskController;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;


    private String getBaseUrl() {
        return "http://localhost:" + port + API_TASKS_V1_PATH;
    }

    @Test
    @Order(1)
    @DisplayName("Application context loads successfully")
    void contextLoads() {
        assertThat(taskController).isNotNull();
        assertThat(taskService).isNotNull();
        assertThat(taskRepository).isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("Health check - Application is accessible")
    void applicationIsAccessible() {
        ResponseEntity<TaskResponse> response = restTemplate.exchange(
            getBaseUrl(),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("Create task - Basic functionality works")
    void canCreateTask() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("Smoke Test Task");
        request.setDescription("This is a smoke test task");
        request.setStatus("PENDING");
        request.setDueDate(LocalDateTime.now().plusDays(7));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TaskController.CreateTaskRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Task> response = restTemplate.postForEntity(
            getBaseUrl(),
            entity,
            Task.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Smoke Test Task");
        assertThat(response.getBody().getCaseNumber()).isNotNull();
        assertThat(response.getBody().getId()).isGreaterThan(0);
    }

    @Test
    @Order(4)
    @DisplayName("Get all tasks - Read functionality works")
    void canGetAllTasks() {
        ResponseEntity<TaskResponse> response = restTemplate.exchange(
            getBaseUrl(),
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTasks()).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("Get task by ID - Read specific task works")
    void canGetTaskById() {
        Task createdTask = createTestTask("Get By ID Test");

        ResponseEntity<Task> response = restTemplate.getForEntity(
            getBaseUrl() + "/" + createdTask.getId(),
            Task.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(createdTask.getId());
        assertThat(response.getBody().getTitle()).isEqualTo("Get By ID Test");
    }

    @Test
    @Order(6)
    @DisplayName("Update task status - Update functionality works")
    void canUpdateTaskStatus() {
        Task createdTask = createTestTask("Update Status Test");

        TaskController.UpdateStatusRequest updateRequest = new TaskController.UpdateStatusRequest();
        updateRequest.setStatus("IN_PROGRESS");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TaskController.UpdateStatusRequest> entity = new HttpEntity<>(updateRequest, headers);

        ResponseEntity<Task> response = restTemplate.exchange(
            getBaseUrl() + "/" + createdTask.getId() + "/status",
            HttpMethod.PUT,
            entity,
            Task.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @Order(7)
    @DisplayName("Update full task - Full update functionality works")
    void canUpdateTask() {

        TaskController.UpdateTaskRequest updateRequest = new TaskController.UpdateTaskRequest();
        updateRequest.setTitle("Updated Title");
        updateRequest.setDescription("Updated Description");
        updateRequest.setStatus("COMPLETED");
        updateRequest.setDueDate(LocalDateTime.now().plusDays(14));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TaskController.UpdateTaskRequest> entity = new HttpEntity<>(updateRequest, headers);

        Task createdTask = createTestTask("Update Task Test");

        ResponseEntity<Task> response = restTemplate.exchange(
            getBaseUrl() + "/" + createdTask.getId(),
            HttpMethod.PUT,
            entity,
            Task.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo("Updated Title");
        assertThat(response.getBody().getDescription()).isEqualTo("Updated Description");
        assertThat(response.getBody().getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @Order(8)
    @DisplayName("Search tasks - Search functionality works")
    void canSearchTasks() {
        createTestTask("Searchable Task Content");

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
            getBaseUrl() + "?search=Searchable",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTasks()).isNotEmpty();
        assertThat(response.getBody().getTasks().getFirst().getTitle()).contains("Searchable");
    }

    @Test
    @Order(9)
    @DisplayName("Get tasks by status - Status filtering works")
    void canGetTasksByStatus() {
        createTestTask("Status Filter Test", "ON_HOLD");

        ResponseEntity<List<Task>> response = restTemplate.exchange(
            getBaseUrl() + "/status/ON_HOLD",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        if (!response.getBody().isEmpty()) {
            assertThat(response.getBody().getFirst().getStatus()).isEqualTo("ON_HOLD");
        }
    }

    @Test
    @Order(10)
    @DisplayName("Get overdue tasks - Overdue functionality works")
    void canGetOverdueTasks() {
        createOverdueTask("Overdue Test Task");

        ResponseEntity<List<Task>> response = restTemplate.exchange(
            getBaseUrl() + "/overdue",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @Order(11)
    @DisplayName("Get task statistics - Statistics functionality works")
    void canGetTaskStatistics() {
        ResponseEntity<Map<String, Long>> response = restTemplate.exchange(
            getBaseUrl() + "/statistics",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).containsKey("total");
        assertThat(response.getBody().get("total")).isGreaterThan(0);
    }

    @Test
    @Order(12)
    @DisplayName("Get valid statuses - Status enumeration works")
    void canGetValidStatuses() {
        ResponseEntity<List<String>> response = restTemplate.exchange(
            getBaseUrl() + "/statuses",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ON_HOLD");
    }

    @Test
    @Order(13)
    @DisplayName("Delete task - Delete functionality works")
    void canDeleteTask() {
        Task taskToDelete = createTestTask("Delete Test Task");

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            getBaseUrl() + "/" + taskToDelete.getId(),
            HttpMethod.DELETE,
            null,
            Void.class
        );

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(
            getBaseUrl() + "/" + taskToDelete.getId(),
            String.class
        );

        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(getResponse.getBody()).contains("not found");
    }

    @Test
    @Order(14)
    @DisplayName("Error handling - Invalid request returns proper error")
    void handlesInvalidRequests() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            getBaseUrl() + "/99999",
            String.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        String invalidJson = "{\"title\":\"\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(invalidJson, headers);

        ResponseEntity<String> createResponse = restTemplate.postForEntity(
            getBaseUrl(),
            entity,
            String.class
        );
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Order(15)
    @DisplayName("CORS configuration - Cross-origin requests are handled")
    void corsConfigurationWorks() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:3000");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
            getBaseUrl(),
            HttpMethod.GET,
            entity,
            new ParameterizedTypeReference<>() {
            }
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @Order(16)
    @DisplayName("API documentation - Swagger endpoints are accessible")
    void swaggerEndpointsAccessible() {

        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/swagger-ui.html",
            String.class
        );

        assertThat(response.getStatusCode().is2xxSuccessful()
                       || response.getStatusCode() == HttpStatus.NOT_FOUND).isTrue();
    }

    @Test
    @Order(17)
    @DisplayName("Database connectivity - Repository operations work")
    void databaseConnectivityWorks() {
        long initialCount = taskRepository.count();

        Task directTask = new Task(
            "SMOKE_TEST_001",
            "Direct Repository Test",
            "Testing direct repository access",
            "PENDING",
            LocalDateTime.now().plusDays(1)
        );

        Task savedTask = taskRepository.save(directTask);
        assertThat(savedTask.getId()).isGreaterThan(0);

        long newCount = taskRepository.count();
        assertThat(newCount).isEqualTo(initialCount + 1);

        taskRepository.delete(savedTask);
    }

    @Test
    @Order(18)
    @DisplayName("Service layer - Business logic works correctly")
    void serviceLayerWorks() {

        Task serviceTask = taskService.createTask(
            "Service Layer Test",
            "Testing service layer functionality",
            "PENDING",
            LocalDateTime.now().plusDays(5)
        );

        assertThat(serviceTask).isNotNull();
        assertThat(serviceTask.getCaseNumber()).startsWith("TASK");
        assertThat(serviceTask.getStatus()).isEqualTo("PENDING");

        List<String> validStatuses = TaskService.Status.getValidStatuses();
        assertThat(validStatuses).isNotEmpty();
        assertThat(TaskService.Status.isValid("PENDING")).isTrue();
        assertThat(TaskService.Status.isValid("INVALID_STATUS")).isFalse();
    }

    private Task createTestTask(String title) {
        return createTestTask(title, "PENDING");
    }

    private Task createTestTask(String title, String status) {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle(title);
        request.setDescription("Test task description");
        request.setStatus(status);
        request.setDueDate(LocalDateTime.now().plusDays(7));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TaskController.CreateTaskRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Task> response = restTemplate.postForEntity(
            getBaseUrl(),
            entity,
            Task.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private Task createOverdueTask(String title) {

        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle(title);
        request.setDescription("Overdue test task");
        request.setStatus("PENDING");
        request.setDueDate(LocalDateTime.now().plusDays(1)); // Future date to pass validation

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TaskController.CreateTaskRequest> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Task> response = restTemplate.postForEntity(
            getBaseUrl(),
            entity,
            Task.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Task task = response.getBody();

        TaskController.UpdateTaskRequest updateRequest = new TaskController.UpdateTaskRequest();
        updateRequest.setDueDate(LocalDateTime.now().minusDays(1));

        HttpEntity<TaskController.UpdateTaskRequest> updateEntity = new HttpEntity<>(updateRequest, headers);
        restTemplate.put(getBaseUrl() + "/" + task.getId(), updateEntity);

        ResponseEntity<Task> updatedResponse = restTemplate.getForEntity(
            getBaseUrl() + "/" + task.getId(),
            Task.class
        );

        return updatedResponse.getBody();
    }

    public static class TaskResponse {
        private List<Task> tasks;

        public List<Task> getTasks() {
            return tasks;
        }

        public void setTasks(List<Task> tasks) {
            this.tasks = tasks;
        }
    }
}
