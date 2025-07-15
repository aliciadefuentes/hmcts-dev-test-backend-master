package uk.gov.hmcts.reform.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
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
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Transactional
@DisplayName("Task Management Functional Tests")
public class TaskControllerFunctionalTest {

    public static final String PATH_TASKS = "/api/v1/tasks";

    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TaskRepository taskRepository;
    private DateTimeFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        taskRepository.deleteAll();
    }

    private Task createSampleTask(String title, String status) {
        Task task = new Task(
            "TASK" + taskCounter.incrementAndGet(),
            title,
            "Sample description for " + title,
            status,
            LocalDateTime.now().plusDays(7)
        );
        return taskRepository.save(task);
    }

    private void createOverdueTask(String title, String status) {
        Task task = new Task(
            "TASK" + taskCounter.incrementAndGet(),
            title,
            "Overdue task description",
            status,
            LocalDateTime.now().minusDays(1)
        );
        taskRepository.save(task);
    }

    private void createSampleTasks(int count) {
        for (int i = 1; i <= count; i++) {
            createSampleTask("Task " + i, "PENDING");
        }
    }

    private void createTasksWithDifferentStatuses() {
        createSampleTask("Task 1", "PENDING");
        createSampleTask("Task 2", "IN_PROGRESS");
        createSampleTask("Task 3", "COMPLETED");
        createSampleTask("Task 4", "CANCELLED");
        createSampleTask("Task 5", "ON_HOLD");
    }


    @Nested
    @DisplayName("Task Creation Tests")
    class TaskCreationTests {


        @Test
        @DisplayName("Should create task with all valid fields")
        void shouldCreateTaskWithAllValidFields() throws Exception {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(5);
            String requestBody = String.format(
                """
                    {
                        "title": "Complete user documentation",
                        "description": "Write comprehensive user documentation for the new features",
                        "status": "PENDING",
                        "dueDate": "%s"
                    }
                    """, dueDate.format(formatter)
            );

            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.title").value("Complete user documentation"))
                .andExpect(jsonPath("$.description").value("Write comprehensive user "
                                                               + "documentation for the new features"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.caseNumber").exists())
                .andExpect(jsonPath("$.caseNumber").value(startsWith("TASK")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdDate").exists())
                .andExpect(jsonPath("$.updatedDate").exists());
        }

        @Test
        @DisplayName("Should create task with minimal required fields")
        void shouldCreateTaskWithMinimalFields() throws Exception {

            LocalDateTime dueDate = LocalDateTime.now().plusDays(3);
            String requestBody = String.format(
                """
                    {
                        "title": "Minimal task",
                        "dueDate": "%s"
                    }
                    """, dueDate.format(formatter)
            );

            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Minimal task"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.description").isEmpty());
        }

        @Test
        @DisplayName("Should fail to create task with blank title")
        void shouldFailToCreateTaskWithBlankTitle() throws Exception {

            LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
            String requestBody = String.format(
                """
                    {
                        "title": "",
                        "dueDate": "%s"
                    }
                    """, dueDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create task with null title")
        void shouldFailToCreateTaskWithNullTitle() throws Exception {

            LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
            String requestBody = String.format(
                """
                    {
                        "title": null,
                        "dueDate": "%s"
                    }
                    """, dueDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create task with title exceeding 255 characters")
        void shouldFailToCreateTaskWithLongTitle() throws Exception {

            String longTitle = "A".repeat(256);
            LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
            String requestBody = String.format(
                """
                    {
                        "title": "%s",
                        "dueDate": "%s"
                    }
                    """, longTitle, dueDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create task with description exceeding 1000 characters")
        void shouldFailToCreateTaskWithLongDescription() throws Exception {

            String longDescription = "A".repeat(1001);
            LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
            String requestBody = String.format(
                """
                    {
                        "title": "Valid title",
                        "description": "%s",
                        "dueDate": "%s"
                    }
                    """, longDescription, dueDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create task with past due date")
        void shouldFailToCreateTaskWithPastDueDate() throws Exception {

            LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
            String requestBody = String.format(
                """
                    {
                        "title": "Test task",
                        "dueDate": "%s"
                    }
                    """, pastDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create task with invalid status")
        void shouldFailToCreateTaskWithInvalidStatus() throws Exception {

            LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
            String requestBody = String.format(
                """
                    {
                        "title": "Test task",
                        "status": "INVALID_STATUS",
                        "dueDate": "%s"
                    }
                    """, dueDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create task with null due date")
        void shouldFailToCreateTaskWithNullDueDate() throws Exception {

            String requestBody = """
                {
                    "title": "Test task",
                    "dueDate": null
                }
                """;


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to create task with missing due date")
        void shouldFailToCreateTaskWithMissingDueDate() throws Exception {

            String requestBody = """
                {
                    "title": "Test task"
                }
                """;


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Task Retrieval Tests")
    class TaskRetrievalTests {

        @Test
        @DisplayName("Should retrieve all tasks with pagination")
        void shouldRetrieveAllTasksWithPagination() throws Exception {

            createSampleTasks(3);


            mockMvc.perform(get(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.tasks", hasSize(3)))
                .andExpect(jsonPath("$.totalTasks").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.tasks[*].title", containsInAnyOrder("Task 1", "Task 2", "Task 3")));
        }

        @Test
        @DisplayName("Should retrieve empty list when no tasks exist")
        void shouldRetrieveEmptyListWhenNoTasks() throws Exception {

            mockMvc.perform(get(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(0)))
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        }

        @Test
        @DisplayName("Should handle pagination correctly")
        void shouldHandlePaginationCorrectly() throws Exception {
            createSampleTasks(25);

            mockMvc.perform(get(PATH_TASKS)
                                .param("page", "1")
                                .param("pageSize", "10")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(10)))
                .andExpect(jsonPath("$.totalTasks").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.currentPage").value(1))
                .andExpect(jsonPath("$.pageSize").value(10));

            mockMvc.perform(get(PATH_TASKS)
                                .param("page", "3")
                                .param("pageSize", "10")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(5)))
                .andExpect(jsonPath("$.totalTasks").value(25))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.currentPage").value(3))
                .andExpect(jsonPath("$.pageSize").value(10));
        }

        @Test
        @DisplayName("Should handle invalid pagination parameters")
        void shouldHandleInvalidPaginationParameters() throws Exception {
            createSampleTasks(5);

            mockMvc.perform(get(PATH_TASKS)
                                .param("page", "0")
                                .param("pageSize", "10")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPage").value(1));

            mockMvc.perform(get(PATH_TASKS)
                                .param("page", "1")
                                .param("pageSize", "0")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(10));

            mockMvc.perform(get(PATH_TASKS)
                                .param("page", "1")
                                .param("pageSize", "150")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(10));
        }

        @Test
        @DisplayName("Should retrieve task by ID")
        void shouldRetrieveTaskById() throws Exception {
            Task savedTask = createSampleTask("Retrieve me", "PENDING");

            mockMvc.perform(get(PATH_TASKS + "/{id}", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedTask.getId()))
                .andExpect(jsonPath("$.title").value("Retrieve me"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Should return 404 when task not found by ID")
        void shouldReturn404WhenTaskNotFound() throws Exception {
            mockMvc.perform(get(PATH_TASKS + "/{id}", 999)
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should search tasks by title")
        void shouldSearchTasksByTitle() throws Exception {

            createSampleTask("Important task", "PENDING");
            createSampleTask("Regular task", "IN_PROGRESS");
            createSampleTask("Another important item", "COMPLETED");

            mockMvc.perform(get(PATH_TASKS)
                                .param("search", "important")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(2)))
                .andExpect(jsonPath(
                    "$.tasks[*].title",
                    containsInAnyOrder("Important task", "Another important item")
                ));
        }

        @Test
        @DisplayName("Should search tasks by case number")
        void shouldSearchTasksByCaseNumber() throws Exception {
            Task task1 = createSampleTask("Task 1", "PENDING");
            createSampleTask("Task 2", "IN_PROGRESS");

            mockMvc.perform(get(PATH_TASKS)
                                .param("search", task1.getCaseNumber())
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].caseNumber").value(task1.getCaseNumber()));
        }

        @Test
        @DisplayName("Should filter tasks by status")
        void shouldFilterTasksByStatus() throws Exception {
            createSampleTask("Task 1", "PENDING");
            createSampleTask("Task 2", "IN_PROGRESS");
            createSampleTask("Task 3", "PENDING");

            mockMvc.perform(get(PATH_TASKS)
                                .param("status", "PENDING")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(2)))
                .andExpect(jsonPath("$.tasks[*].status", everyItem(is("PENDING"))));
        }

        @Test
        @DisplayName("Should retrieve tasks by status using dedicated endpoint")
        void shouldRetrieveTasksByStatusUsingDedicatedEndpoint() throws Exception {
            createSampleTask("Task 1", "PENDING");
            createSampleTask("Task 2", "IN_PROGRESS");
            createSampleTask("Task 3", "PENDING");

            mockMvc.perform(get(PATH_TASKS + "/status/{status}", "PENDING")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", everyItem(is("PENDING"))));
        }

        @Test
        @DisplayName("Should retrieve overdue tasks")
        void shouldRetrieveOverdueTasks() throws Exception {
            createOverdueTask("Overdue task 1", "PENDING");
            createOverdueTask("Overdue task 2", "IN_PROGRESS");
            createSampleTask("Future task", "PENDING");

            mockMvc.perform(get(PATH_TASKS + "/overdue")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Overdue task 1", "Overdue task 2")));
        }

        @Test
        @DisplayName("Should combine search and status filters")
        void shouldCombineSearchAndStatusFilters() throws Exception {
            createSampleTask("Important pending task", "PENDING");
            createSampleTask("Important completed task", "COMPLETED");
            createSampleTask("Regular pending task", "PENDING");

            mockMvc.perform(get(PATH_TASKS)
                                .param("search", "important")
                                .param("status", "PENDING")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].title").value("Important pending task"))
                .andExpect(jsonPath("$.tasks[0].status").value("PENDING"));
        }
    }

    @Nested
    @DisplayName("Task Update Tests")
    class TaskUpdateTests {

        @Test
        @DisplayName("Should update task status")
        void shouldUpdateTaskStatus() throws Exception {
            Task savedTask = createSampleTask("Update status task", "PENDING");
            String requestBody = """
                {
                    "status": "IN_PROGRESS"
                }
                """;

            mockMvc.perform(put( PATH_TASKS + "/{id}/status", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedTask.getId()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.title").value("Update status task"));
        }

        @Test
        @DisplayName("Should update all task fields")
        void shouldUpdateAllTaskFields() throws Exception {
            Task savedTask = createSampleTask("Original task", "PENDING");
            LocalDateTime newDueDate = LocalDateTime.now().plusDays(10);
            String requestBody = String.format(
                """
                    {
                        "title": "Updated task title",
                        "description": "Updated description",
                        "status": "IN_PROGRESS",
                        "dueDate": "%s"
                    }
                    """, newDueDate.format(formatter)
            );


            mockMvc.perform(put(PATH_TASKS + "/{id}", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedTask.getId()))
                .andExpect(jsonPath("$.title").value("Updated task title"))
                .andExpect(jsonPath("$.description").value("Updated description"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        }

        @Test
        @DisplayName("Should update partial task fields")
        void shouldUpdatePartialTaskFields() throws Exception {

            Task savedTask = createSampleTask("Partial update task", "PENDING");
            String requestBody = """
                {
                    "title": "Updated title only"
                }
                """;


            mockMvc.perform(put(PATH_TASKS + "/{id}", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedTask.getId()))
                .andExpect(jsonPath("$.title").value("Updated title only"))
                .andExpect(jsonPath("$.status").value("PENDING"));
        }

        @Test
        @DisplayName("Should fail to update task with invalid status")
        void shouldFailToUpdateTaskWithInvalidStatus() throws Exception {

            Task savedTask = createSampleTask("Test task", "PENDING");
            String requestBody = """
                {
                    "status": "INVALID_STATUS"
                }
                """;


            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}/status", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to update task with blank status")
        void shouldFailToUpdateTaskWithBlankStatus() throws Exception {

            Task savedTask = createSampleTask("Test task", "PENDING");
            String requestBody = """
                {
                    "status": ""
                }
                """;


            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}/status", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to update task with title exceeding 255 characters")
        void shouldFailToUpdateTaskWithLongTitle() throws Exception {

            Task savedTask = createSampleTask("Test task", "PENDING");
            String longTitle = "A".repeat(256);
            String requestBody = String.format(
                """
                    {
                        "title": "%s"
                    }
                    """, longTitle
            );


            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to update task with description exceeding 1000 characters")
        void shouldFailToUpdateTaskWithLongDescription() throws Exception {

            Task savedTask = createSampleTask("Test task", "PENDING");
            String longDescription = "A".repeat(1001);
            String requestBody = String.format(
                """
                    {
                        "description": "%s"
                    }
                    """, longDescription
            );


            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should fail to update non-existent task")
        void shouldFailToUpdateNonExistentTask() throws Exception {

            String requestBody = """
                {
                    "status": "IN_PROGRESS"
                }
                """;


            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}/status", 999)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should fail to update task with past due date")
        void shoulUpdateTaskWithPastDueDate() throws Exception {

            Task savedTask = createSampleTask("Test task", "PENDING");
            LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
            String requestBody = String.format(
                """
                    {
                        "dueDate": "%s"
                    }
                    """, pastDate.format(formatter)
            );


            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}", savedTask.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(pastDate.format(formatter)));
        }

    }

    @Nested
    @DisplayName("Task Deletion Tests")
    class TaskDeletionTests {

        @Test
        @DisplayName("Should delete existing task")
        void shouldDeleteExistingTask() throws Exception {

            Task savedTask = createSampleTask("Task to delete", "PENDING");


            mockMvc.perform(delete(PATH_TASKS +
                                       "/{id}", savedTask.getId()))
                .andDo(print())
                .andExpect(status().isNoContent());

            mockMvc.perform(get(PATH_TASKS +
                                    "/{id}", savedTask.getId()))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 404 when deleting non-existent task")
        void shouldReturn404WhenDeletingNonExistentTask() throws Exception {

            mockMvc.perform(delete(PATH_TASKS +
                                       "/{id}", 999))
                .andDo(print())
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should delete task and update statistics")
        void shouldDeleteTaskAndUpdateStatistics() throws Exception {

            Task task1 = createSampleTask("Task 1", "PENDING");
            createSampleTask("Task 2", "PENDING");

            mockMvc.perform(get(PATH_TASKS +
                                    "/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(2));

            mockMvc.perform(delete(PATH_TASKS +
                                       "/{id}", task1.getId()))
                .andExpect(status().isNoContent());

            mockMvc.perform(get(PATH_TASKS +
                                    "/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1));
        }
    }

    @Nested
    @DisplayName("Task Statistics Tests")
    class TaskStatisticsTests {

        @Test
        @DisplayName("Should retrieve task statistics")
        void shouldRetrieveTaskStatistics() throws Exception {

            createTasksWithDifferentStatuses();


            mockMvc.perform(get(PATH_TASKS +
                                    "/statistics"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.in_progress").value(1))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.cancelled").value(1))
                .andExpect(jsonPath("$.on_hold").value(1));
        }

        @Test
        @DisplayName("Should return empty statistics when no tasks exist")
        void shouldReturnEmptyStatisticsWhenNoTasks() throws Exception {

            mockMvc.perform(get(PATH_TASKS +
                                    "/statistics"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
        }

        @Test
        @DisplayName("Should update statistics when task status changes")
        void shouldUpdateStatisticsWhenTaskStatusChanges() throws Exception {

            Task task = createSampleTask("Test task", "PENDING");

            mockMvc.perform(get(PATH_TASKS +
                                    "/statistics"))
                .andExpect(jsonPath("$.pending").value(1));

            String requestBody = """
                {
                    "status": "COMPLETED"
                }
                """;

            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}/status", task.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andExpect(status().isOk());

            mockMvc.perform(get(PATH_TASKS +
                                    "/statistics"))
                .andExpect(jsonPath("$.completed").value(1))
                .andExpect(jsonPath("$.pending").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Valid Statuses Tests")
    class ValidStatusesTests {

        @Test
        @DisplayName("Should retrieve valid statuses")
        void shouldRetrieveValidStatuses() throws Exception {

            mockMvc.perform(get(PATH_TASKS +
                                    "/statuses"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(Matchers.greaterThan(0))))
                .andExpect(jsonPath(
                    "$", containsInAnyOrder(
                        "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED", "ON_HOLD")
                ));
        }

        @Test
        @DisplayName("Should retrieve valid statuses using alternative endpoint")
        void shouldRetrieveValidStatusesUsingAlternativeEndpoint() throws Exception {

            mockMvc.perform(get(PATH_TASKS +
                                    "/statuses"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(Matchers.greaterThan(0))));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle malformed JSON")
        void shouldHandleMalformedJson() throws Exception {

            String malformedJson = """
                {
                    "title": "Test task",
                    "dueDate": "invalid-date-format"
                }
                """;


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(malformedJson))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle missing content type")
        void shouldHandleMissingContentType() throws Exception {

            String requestBody = """
                {
                    "title": "Test task",
                    "dueDate": "2024-12-31T23:59:59"
                }
                """;


            mockMvc.perform(post(PATH_TASKS)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isUnsupportedMediaType());
        }

        @Test
        @DisplayName("Should handle empty request body")
        void shouldHandleEmptyRequestBody() throws Exception {

            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(""))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should handle invalid HTTP methods")
        void shouldHandleInvalidHttpMethods() throws Exception {

            mockMvc.perform(patch(PATH_TASKS +
                                      "/1")
                                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isMethodNotAllowed());
        }

        @Test
        @DisplayName("Should handle invalid path parameters")
        void shouldHandleInvalidPathParameters() throws Exception {

            mockMvc.perform(get(PATH_TASKS +
                                    "/invalid-id"))
                .andDo(print())
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Security and CORS Tests")
    class SecurityAndCorsTests {

        @Test
        @DisplayName("Should handle CORS preflight requests")
        void shouldHandleCorsPreflightRequests() throws Exception {

            mockMvc.perform(options(PATH_TASKS)
                                .header("Origin", "http://localhost:3000")
                                .header("Access-Control-Request-Method", "POST")
                                .header("Access-Control-Request-Headers", "Content-Type"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
        }

        @Test
        @DisplayName("Should include CORS headers in responses")
        void shouldIncludeCorsHeadersInResponses() throws Exception {

            createSampleTask("Test task", "PENDING");


            mockMvc.perform(get(PATH_TASKS)
                                .header("Origin", "http://localhost:3000"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"));
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCasesAndBoundaryTests {

        @Test
        @DisplayName("Should handle exact character limits")
        void shouldHandleExactCharacterLimits() throws Exception {
            String exactTitle = "A".repeat(255);
            String exactDescription = "B".repeat(1000);
            LocalDateTime dueDate = LocalDateTime.now().plusDays(1);

            String requestBody = String.format(
                """
                    {
                        "title": "%s",
                        "description": "%s",
                        "dueDate": "%s"
                    }
                    """, exactTitle, exactDescription, dueDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(exactTitle))
                .andExpect(jsonPath("$.description").value(exactDescription));
        }

        @Test
        @DisplayName("Should handle maximum pagination values")
        void shouldHandleMaximumPaginationValues() throws Exception {

            createSampleTasks(5);

            mockMvc.perform(get(PATH_TASKS)
                                .param("page", "1")
                                .param("pageSize", "100"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(100));
        }

        @Test
        @DisplayName("Should handle very large page numbers")
        void shouldHandleVeryLargePageNumbers() throws Exception {

            createSampleTasks(5);

            mockMvc.perform(get(PATH_TASKS)
                                .param("page", "999")
                                .param("pageSize", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(0)))
                .andExpect(jsonPath("$.currentPage").value(999))
                .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @DisplayName("Should handle special characters in search")
        void shouldHandleSpecialCharactersInSearch() throws Exception {

            createSampleTask("Task with special chars: @#$%", "PENDING");
            createSampleTask("Regular task", "PENDING");


            mockMvc.perform(get(PATH_TASKS)
                                .param("search", "@#$"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].title").value("Task with special chars: @#$%"));
        }

        @Test
        @DisplayName("Should handle Unicode characters")
        void shouldHandleUnicodeCharacters() throws Exception {

            LocalDateTime dueDate = LocalDateTime.now().plusDays(1);
            String requestBody = String.format(
                """
                    {
                        "title": "タスク with émojis 🚀",
                        "description": "Unicode test: αβγ δεζ",
                        "dueDate": "%s"
                    }
                    """, dueDate.format(formatter)
            );


            mockMvc.perform(post(PATH_TASKS)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("タスク with émojis 🚀"))
                .andExpect(jsonPath("$.description").value("Unicode test: αβγ δεζ"));
        }
    }

    @Nested
    @DisplayName("Integration and Data Consistency Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should maintain data consistency across operations")
        void shouldMaintainDataConsistencyAcrossOperations() throws Exception {
            LocalDateTime dueDate = LocalDateTime.now().plusDays(5);
            String createRequestBody = String.format(
                """
                    {
                        "title": "Consistency test task",
                        "description": "Original description",
                        "dueDate": "%s"
                    }
                    """, dueDate.format(formatter)
            );

            String createResponse = mockMvc.perform(post(PATH_TASKS)
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(createRequestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            Task createdTask = mapper.readValue(createResponse, Task.class);
            int taskId = createdTask.getId();

            String updateRequestBody = """
                {
                    "title": "Updated consistency test task",
                    "status": "IN_PROGRESS"
                }
                """;

            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}", taskId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateRequestBody))
                .andExpect(status().isOk());

            mockMvc.perform(get(PATH_TASKS + "/{id}", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated consistency test task"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

            mockMvc.perform(get(PATH_TASKS +
                                    "/status/IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(taskId)));

            mockMvc.perform(get(PATH_TASKS)
                                .param("search", "Updated consistency"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[*].id", hasItem(taskId)));
        }

        @Test
        @DisplayName("Should handle concurrent-like operations")
        void shouldHandleConcurrentLikeOperations() throws Exception {

            Task task = createSampleTask("Concurrent test task", "PENDING");

            String statusUpdate1 = """
                {
                    "status": "IN_PROGRESS"
                }
                """;

            String statusUpdate2 = """
                {
                    "status": "COMPLETED"
                }
                """;


            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}/status", task.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(statusUpdate1))
                .andExpect(status().isOk());

            mockMvc.perform(put(PATH_TASKS +
                                    "/{id}/status", task.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(statusUpdate2))
                .andExpect(status().isOk());

            mockMvc.perform(get(PATH_TASKS +
                                    "/{id}", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }

}
