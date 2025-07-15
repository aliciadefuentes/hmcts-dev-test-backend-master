package uk.gov.hmcts.reform.dev.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TaskControllerIntegrationTest {

    private static final String BASE_URL = "/api/v1/tasks";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());
        taskRepository.deleteAll();
    }

    @Test
    void createTask_WithValidData_ShouldReturnCreated() throws Exception {
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        String requestBody = createTaskRequestJson(
            "Test Task",
            "Test Description",
            TaskService.Status.PENDING.name(),
            dueDate
        );

        mockMvc.perform(post(BASE_URL)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(requestBody))
            .andDo(print())
            .andExpect(status().isCreated())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.caseNumber").exists())
            .andExpect(jsonPath("$.title").value("Test Task"))
            .andExpect(jsonPath("$.description").value("Test Description"))
            .andExpect(jsonPath("$.status").value(TaskService.Status.PENDING.name()))
            .andExpect(jsonPath("$.dueDate").exists())
            .andExpect(jsonPath("$.createdDate").exists())
            .andReturn();

        List<Task> tasks = taskRepository.findAll();
        assertEquals(1, tasks.size());
        assertEquals("Test Task", tasks.getFirst().getTitle());
    }

    @Test
    void createTask_WithMissingTitle_ShouldReturnBadRequest() throws Exception {

        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        String requestBody = createTaskRequestJson("", "Test Description", TaskService.Status.PENDING.name(), dueDate);

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").exists());
    }

    @Test
    void createTask_WithTitleTooLong_ShouldReturnBadRequest() throws Exception {

        String longTitle = "a".repeat(256);
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        String requestBody = createTaskRequestJson(longTitle, "Test Description", "PENDING", dueDate);

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").exists());
    }

    @Test
    void createTask_WithDescriptionTooLong_ShouldReturnBadRequest() throws Exception {

        String longDescription = "a".repeat(1001);
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        String requestBody = createTaskRequestJson("Test Task", longDescription, "PENDING", dueDate);

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.description").exists());
    }

    @Test
    void createTask_WithMissingDueDate_ShouldReturnBadRequest() throws Exception {

        String requestBody = """
            {
                "title": "Test Task",
                "description": "Test Description",
                "status": "PENDING"
            }
            """;

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.dueDate").exists());
    }

    @Test
    void createTask_WithInvalidStatus_ShouldReturnBadRequest() throws Exception {

        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        String requestBody = createTaskRequestJson("Test Task", "Test Description", "INVALID_STATUS", dueDate);

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Invalid status")));
    }

    @Test
    void getAllTasks_WithNoTasks_ShouldReturnEmptyTaskPageResponse() throws Exception {

        mockMvc.perform(get(BASE_URL))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks").isEmpty())
            .andExpect(jsonPath("$.totalTasks").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    void getAllTasks_WithMultipleTasks_ShouldReturnAllTasks() throws Exception {

        createTestTask("Task 1", TaskService.Status.PENDING.name());
        createTestTask("Task 2", TaskService.Status.IN_PROGRESS.name());
        createTestTask("Task 3", TaskService.Status.COMPLETED.name());

        mockMvc.perform(get(BASE_URL))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(3))
            .andExpect(jsonPath("$.totalTasks").value(3))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.pageSize").value(10))
            .andExpect(jsonPath("$.tasks[*].title").value(containsInAnyOrder("Task 1", "Task 2", "Task 3")));
    }

    @Test
    void getAllTasks_WithSearchParameter_ShouldReturnFilteredTasks() throws Exception {

        createTestTask("Important Task", "PENDING");
        createTestTask("Regular Task", "IN_PROGRESS");
        createTestTask("Another Important Item", "COMPLETED");

        mockMvc.perform(get(BASE_URL)
                            .param("search", "Important"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(2))
            .andExpect(jsonPath("$.totalTasks").value(2))
            .andExpect(jsonPath("$.tasks[*].title").value(containsInAnyOrder("Important Task",
                                                                             "Another Important Item")));
    }

    @Test
    void getAllTasks_WithStatusFilter_ShouldReturnFilteredTasks() throws Exception {

        createTestTask("Pending Task 1", "PENDING");
        createTestTask("Pending Task 2", "PENDING");
        createTestTask("In Progress Task", "IN_PROGRESS");

        mockMvc.perform(get(BASE_URL)
                            .param("status", "PENDING"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(2))
            .andExpect(jsonPath("$.totalTasks").value(2))
            .andExpect(jsonPath("$.tasks[*].status").value(everyItem(equalTo("PENDING"))));
    }

    @Test
    void getAllTasks_WithSearchAndStatusFilter_ShouldReturnFilteredTasks() throws Exception {

        createTestTask("Important Pending Task", "PENDING");
        createTestTask("Important Progress Task", "IN_PROGRESS");
        createTestTask("Regular Pending Task", "PENDING");

        mockMvc.perform(get(BASE_URL)
                            .param("search", "Important")
                            .param("status", "PENDING"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(1))
            .andExpect(jsonPath("$.totalTasks").value(1))
            .andExpect(jsonPath("$.tasks[0].title").value("Important Pending Task"));
    }

    @Test
    void getAllTasks_WithPagination_ShouldReturnCorrectPage() throws Exception {

        for (int i = 1; i <= 15; i++) {
            createTestTask("Task " + i, "PENDING");
        }

        mockMvc.perform(get(BASE_URL)
                            .param("page", "1")
                            .param("pageSize", "10"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(10))
            .andExpect(jsonPath("$.totalTasks").value(15))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.pageSize").value(10));

        mockMvc.perform(get(BASE_URL)
                            .param("page", "2")
                            .param("pageSize", "10"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(5))
            .andExpect(jsonPath("$.currentPage").value(2));
    }

    @Test
    void getAllTasks_WithInvalidPageParameters_ShouldUseDefaults() throws Exception {

        createTestTask("Test Task", "PENDING");

        mockMvc.perform(get(BASE_URL)
                            .param("page", "-1")
                            .param("pageSize", "0"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.pageSize").value(10));

        mockMvc.perform(get(BASE_URL)
                            .param("pageSize", "200"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    void getTaskById_WithValidId_ShouldReturnTask() throws Exception {

        Task savedTask = createTestTask("Test Task", "PENDING");

        mockMvc.perform(get(BASE_URL + "/" + savedTask.getId()))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(savedTask.getId()))
            .andExpect(jsonPath("$.title").value("Test Task"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getTaskById_WithInvalidId_ShouldReturnNotFound() throws Exception {

        mockMvc.perform(get(BASE_URL + "/999"))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    void updateTaskStatus_WithValidData_ShouldReturnUpdatedTask() throws Exception {

        Task savedTask = createTestTask("Test Task", "PENDING");
        String requestBody = """
            {
                "status": "IN_PROGRESS"
            }
            """;

        mockMvc.perform(put(BASE_URL + "/" + savedTask.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(savedTask.getId()))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        Task updatedTask = taskRepository.findById(savedTask.getId()).orElseThrow();
        assertEquals("IN_PROGRESS", updatedTask.getStatus());
    }

    @Test
    void updateTaskStatus_WithInvalidStatus_ShouldReturnBadRequest() throws Exception {

        Task savedTask = createTestTask("Test Task", "PENDING");
        String requestBody = """
            {
                "status": "INVALID_STATUS"
            }
            """;

        mockMvc.perform(put(BASE_URL + "/" + savedTask.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Invalid status")));
    }

    @Test
    void updateTaskStatus_WithEmptyStatus_ShouldReturnBadRequest() throws Exception {

        Task savedTask = createTestTask("Test Task", "PENDING");
        String requestBody = """
            {
                "status": ""
            }
            """;

        mockMvc.perform(put(BASE_URL + "/" + savedTask.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.status").exists());
    }

    @Test
    void updateTask_WithValidData_ShouldReturnUpdatedTask() throws Exception {

        Task savedTask = createTestTask("Original Task", "PENDING");
        LocalDateTime newDueDate = LocalDateTime.now().plusDays(10);
        String requestBody = createUpdateTaskRequestJson(
            "Updated Task",
            "Updated Description",
            "IN_PROGRESS",
            newDueDate
        );

        mockMvc.perform(put(BASE_URL + "/" + savedTask.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(savedTask.getId()))
            .andExpect(jsonPath("$.title").value("Updated Task"))
            .andExpect(jsonPath("$.description").value("Updated Description"))
            .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        Task updatedTask = taskRepository.findById(savedTask.getId()).orElseThrow();
        assertEquals("Updated Task", updatedTask.getTitle());
        assertEquals("Updated Description", updatedTask.getDescription());
        assertEquals("IN_PROGRESS", updatedTask.getStatus());
    }

    @Test
    void updateTask_WithPartialData_ShouldUpdateOnlyProvidedFields() throws Exception {

        Task savedTask = createTestTask("Original Task", "PENDING");
        String requestBody = """
            {
                "title": "Updated Title Only"
            }
            """;

        mockMvc.perform(put(BASE_URL + "/" + savedTask.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title Only"))
            .andExpect(jsonPath("$.status").value("PENDING"));

        Task updatedTask = taskRepository.findById(savedTask.getId()).orElseThrow();
        assertEquals("Updated Title Only", updatedTask.getTitle());
        assertEquals("PENDING", updatedTask.getStatus());
    }

    @Test
    void updateTask_WithInvalidId_ShouldReturnNotFound() throws Exception {
        String requestBody = """
            {
                "title": "Updated Task"
            }
            """;

        mockMvc.perform(put(BASE_URL + "/999")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteTask_WithValidId_ShouldReturnNoContent() throws Exception {

        Task savedTask = createTestTask("Test Task", "PENDING");

        mockMvc.perform(delete(BASE_URL + "/" + savedTask.getId()))
            .andDo(print())
            .andExpect(status().isNoContent());

        assertFalse(taskRepository.existsById(savedTask.getId()));
    }

    @Test
    void deleteTask_WithInvalidId_ShouldReturnNotFound() throws Exception {

        mockMvc.perform(delete(BASE_URL + "/999"))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    void getTasksByStatus_WithValidStatus_ShouldReturnFilteredTasks() throws Exception {

        createTestTask("Pending Task 1", "PENDING");
        createTestTask("In Progress Task", "IN_PROGRESS");
        createTestTask("Pending Task 2", "PENDING");

        mockMvc.perform(get(BASE_URL + "/status/PENDING"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].status").value(everyItem(equalTo("PENDING"))))
            .andExpect(jsonPath("$[*].title").value(containsInAnyOrder("Pending Task 1", "Pending Task 2")));
    }

    @Test
    void getTasksByStatus_WithCaseInsensitiveStatus_ShouldReturnFilteredTasks() throws Exception {

        createTestTask("Pending Task", "PENDING");

        mockMvc.perform(get(BASE_URL + "/status/pending"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void getOverdueTasks_ShouldReturnOnlyOverdueTasks() throws Exception {

        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);

        createTestTaskWithDueDate("Overdue Task 1", "PENDING", pastDate);
        createTestTaskWithDueDate("Overdue Task 2", "IN_PROGRESS", pastDate);
        createTestTaskWithDueDate("Completed Overdue Task", "COMPLETED", pastDate);
        createTestTaskWithDueDate("Future Task", "PENDING", LocalDateTime.now().plusDays(1));

        mockMvc.perform(get(BASE_URL + "/overdue"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].title").value(containsInAnyOrder("Overdue Task 1",
                                                                       "Overdue Task 2")));
    }

    @Test
    void getTaskStatistics_ShouldReturnCorrectCounts() throws Exception {

        createTestTask("Pending Task 1", "PENDING");
        createTestTask("Pending Task 2", "PENDING");
        createTestTask("In Progress Task", "IN_PROGRESS");
        createTestTask("Completed Task", "COMPLETED");
        createTestTaskWithDueDate("Overdue Task", "PENDING", LocalDateTime.now().minusDays(1));

        mockMvc.perform(get(BASE_URL + "/statistics"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.pending").value(3))
            .andExpect(jsonPath("$.in_progress").value(1))
            .andExpect(jsonPath("$.completed").value(1))
            .andExpect(jsonPath("$.overdue").value(1));
    }

    @Test
    void getTaskStatuses_ShouldReturnValidStatuses() throws Exception {

        mockMvc.perform(get(BASE_URL + "/statuses"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").value(containsInAnyOrder(
                "PENDING",
                "IN_PROGRESS",
                "COMPLETED",
                "CANCELLED",
                "ON_HOLD"
            )));
    }

    @Test
    void getValidStatuses_ShouldReturnAllValidStatuses() throws Exception {

        mockMvc.perform(get(BASE_URL + "/statuses"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$").value(containsInAnyOrder(
                "PENDING",
                "IN_PROGRESS",
                "COMPLETED",
                "CANCELLED",
                "ON_HOLD"
            )));
    }

    @Test
    void corsPreflightRequest_ShouldBeAllowed() throws Exception {
        mockMvc.perform(options(BASE_URL)
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "Content-Type"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test
    void createTask_WithInvalidJson_ShouldReturnBadRequest() throws Exception {

        String invalidJson = "{ invalid json }";

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidJson))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateTask_WithNullValues_ShouldHandleGracefully() throws Exception {

        Task savedTask = createTestTask("Original Task", "PENDING");
        String requestBody = """
            {
                "title": null,
                "description": null,
                "status": null,
                "dueDate": null
            }
            """;

        mockMvc.perform(put(BASE_URL + "/" + savedTask.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Original Task"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void updateTask_WithEmptyRequestBody_ShouldReturnBadRequest() throws Exception {

        Task savedTask = createTestTask("Test Task", "PENDING");
        String requestBody = "{}";

        mockMvc.perform(put(BASE_URL + "/" + savedTask.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    void getTaskById_WithInvalidIdFormat_ShouldReturnBadRequest() throws Exception {

        mockMvc.perform(get(BASE_URL + "/invalid-id"))
            .andDo(print())
            .andExpect(status().isBadRequest());
    }

    @Test
    void getTaskById_WithMaxIntId_ShouldReturnNotFound() throws Exception {

        mockMvc.perform(get(BASE_URL + "/" + Integer.MAX_VALUE))
            .andDo(print())
            .andExpect(status().isNotFound());
    }

    @Test
    void createTask_WithWrongContentType_ShouldReturnUnsupportedMediaType() throws Exception {

        String requestBody = createTaskRequestJson("Test", "Desc", "PENDING", LocalDateTime.now().plusDays(1));

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void createTask_WithoutContentType_ShouldReturnUnsupportedMediaType() throws Exception {

        String requestBody = createTaskRequestJson("Test", "Desc", "PENDING", LocalDateTime.now().plusDays(1));

        mockMvc.perform(post(BASE_URL)
                            .content(requestBody))
            .andDo(print())
            .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void patchTask_ShouldReturnMethodNotAllowed() throws Exception {

        mockMvc.perform(patch(BASE_URL + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
            .andDo(print())
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void createTask_WithDuplicateData_ShouldCreateSuccessfully() throws Exception {

        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        String requestBody = createTaskRequestJson("Duplicate Task", "Description", "PENDING", dueDate);

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andExpect(status().isCreated());

        mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
            .andExpect(status().isCreated());
    }

    @Test
    void getAllTasks_WithSpecialCharactersInSearch_ShouldHandleGracefully() throws Exception {

        createTestTask("Task with @#$%", "PENDING");
        createTestTask("Normal Task", "PENDING");

        mockMvc.perform(get(BASE_URL)
                            .param("search", "@#$%"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks.length()").value(1))
            .andExpect(jsonPath("$.tasks[0].title").value("Task with @#$%"));
    }

    @Test
    void getAllTasks_WithSqlInjectionAttempt_ShouldNotCauseError() throws Exception {

        createTestTask("Normal Task", "PENDING");
        String sqlInjection = "'; DROP TABLE tasks; --";

        mockMvc.perform(get(BASE_URL)
                            .param("search", sqlInjection))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray());
    }

    private Task createTestTask(String title, String status) {
        LocalDateTime dueDate = LocalDateTime.now().plusDays(7);
        return createTestTaskWithDueDate(title, status, dueDate);
    }

    private Task createTestTaskWithDueDate(String title, String status, LocalDateTime dueDate) {
        Task task = new Task();
        task.setCaseNumber("TASK" + UUID.randomUUID());
        task.setTitle(title);
        task.setDescription("Test description for " + title);
        task.setStatus(status);
        task.setDueDate(dueDate);
        return taskRepository.save(task);
    }

    private String createTaskRequestJson(String title, String description, String status, LocalDateTime dueDate) {
        return String.format(
            """
                {
                    "title": "%s",
                    "description": "%s",
                    "status": "%s",
                    "dueDate": "%s"
                }
                """, title, description, status, dueDate.format(DATE_FORMAT)
        );
    }

    private String createUpdateTaskRequestJson(String title, String description, String status, LocalDateTime dueDate) {
        return String.format(
            """
                {
                    "title": "%s",
                    "description": "%s",
                    "status": "%s",
                    "dueDate": "%s"
                }
                """, title, description, status, dueDate.format(DATE_FORMAT)
        );
    }
}
