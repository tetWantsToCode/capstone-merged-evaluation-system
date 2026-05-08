package group9.advisor_eval_system.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.forms.v1.Forms;
import com.google.api.services.forms.v1.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import group9.advisor_eval_system.entity.QuestionnaireItem;
import group9.advisor_eval_system.entity.QuestionnaireSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleFormsService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "Adviser Evaluation System";

    private static final Pattern ACTIVATION_URL_PATTERN = Pattern.compile(
            "https://console\\.developers\\.google\\.com/apis/api/forms\\.googleapis\\.com/overview\\?project=\\d+",
            Pattern.CASE_INSENSITIVE);

    private final GoogleAuthService googleAuthService;

    /**
     * Create a Google Form using the teacher's OAuth token
     */
    public Form createGoogleForm(Long teacherId, String title, String description, List<QuestionnaireItem> questions) {
        try {
            String accessToken = googleAuthService.getValidAccessToken(teacherId);
            Forms formsService = getFormsService(accessToken);

            // Create form structure
            Form form = new Form();
            Info info = new Info();
            info.setTitle(title);
            if (description != null && !description.isEmpty()) {
                info.setDocumentTitle(title);
            }
            form.setInfo(info);

            // Create the form
            Form createdForm = formsService.forms().create(form).execute();
            log.info("Created Google Form with ID: {}", createdForm.getFormId());

            // Add questions to the form
            log.info("Received {} questions to add to form", questions != null ? questions.size() : 0);
            if (questions != null && !questions.isEmpty()) {
                addQuestionsToForm(formsService, createdForm.getFormId(), questions);
            } else {
                log.warn("No questions provided to add to Google Form");
            }

            // Retrieve the updated form with responder URI
            Form updatedForm = formsService.forms().get(createdForm.getFormId()).execute();

            return updatedForm;

        } catch (Exception e) {
            log.error("Error creating Google Form", e);

            if (e instanceof GoogleJsonResponseException gjre) {
                var details = gjre.getDetails();
                int status = gjre.getStatusCode();
                String message = details != null ? details.getMessage() : gjre.getMessage();

                // Most common setup issue: API not enabled in the Google Cloud project for this
                // OAuth client.
                if (status == 403 && message != null
                        && (message.contains("SERVICE_DISABLED") || message.contains("accessNotConfigured")
                                || message.contains("disabled") || message.contains("Enable it"))) {
                    String activationUrl = null;
                    try {
                        // Best-effort: try to extract the console activation URL from any error detail
                        // string.
                        if (details != null && details.getDetails() != null) {
                            for (Object d : details.getDetails()) {
                                String s = String.valueOf(d);
                                Matcher m = ACTIVATION_URL_PATTERN.matcher(s);
                                if (m.find()) {
                                    activationUrl = m.group(0);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }

                    if (activationUrl == null) {
                        Matcher m = ACTIVATION_URL_PATTERN.matcher(message);
                        if (m.find()) {
                            activationUrl = m.group(0);
                        }
                    }

                    String extra = activationUrl != null ? (" Enable it here: " + activationUrl) : "";
                    throw new RuntimeException(
                            "Google Forms API is disabled for your Google Cloud project." +
                                    extra +
                                    " After enabling, wait a few minutes for it to propagate, then retry.",
                            e);
                }

                throw new RuntimeException("Failed to create Google Form (HTTP " + status + "): "
                        + (message != null ? message : "Unknown error"), e);
            }

            throw new RuntimeException("Failed to create Google Form: " + e.getMessage(), e);
        }
    }

    /**
     * Create a Google Form with sections using page breaks
     */
    public Form createGoogleFormWithSections(Long teacherId, String title, String description, 
                                             List<QuestionnaireItem> questions, 
                                             List<QuestionnaireSection> sections) {
        try {
            String accessToken = googleAuthService.getValidAccessToken(teacherId);
            Forms formsService = getFormsService(accessToken);

            // Create form structure
            Form form = new Form();
            Info info = new Info();
            info.setTitle(title);
            if (description != null && !description.isEmpty()) {
                info.setDocumentTitle(title);
            }
            form.setInfo(info);

            // Create the form
            Form createdForm = formsService.forms().create(form).execute();
            log.info("Created Google Form with ID: {}", createdForm.getFormId());

            // Add questions and sections with page breaks
            if ((sections != null && !sections.isEmpty()) || (questions != null && !questions.isEmpty())) {
                addQuestionsAndSectionsToForm(formsService, createdForm.getFormId(), questions, sections, accessToken);
            } else {
                log.warn("No questions or sections provided to add to Google Form");
            }

            // Retrieve the updated form with responder URI
            Form updatedForm = formsService.forms().get(createdForm.getFormId()).execute();

            return updatedForm;

        } catch (Exception e) {
            log.error("Error creating Google Form with sections", e);

            if (e instanceof GoogleJsonResponseException gjre) {
                var details = gjre.getDetails();
                int status = gjre.getStatusCode();
                String message = details != null ? details.getMessage() : gjre.getMessage();

                if (status == 403 && message != null
                        && (message.contains("SERVICE_DISABLED") || message.contains("accessNotConfigured")
                                || message.contains("disabled") || message.contains("Enable it"))) {
                    String activationUrl = null;
                    try {
                        if (details != null && details.getDetails() != null) {
                            for (Object d : details.getDetails()) {
                                String s = String.valueOf(d);
                                Matcher m = ACTIVATION_URL_PATTERN.matcher(s);
                                if (m.find()) {
                                    activationUrl = m.group(0);
                                    break;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // ignore
                    }

                    if (activationUrl == null) {
                        Matcher m = ACTIVATION_URL_PATTERN.matcher(message);
                        if (m.find()) {
                            activationUrl = m.group(0);
                        }
                    }

                    String extra = activationUrl != null ? (" Enable it here: " + activationUrl) : "";
                    throw new RuntimeException(
                            "Google Forms API is disabled for your Google Cloud project." +
                                    extra +
                                    " After enabling, wait a few minutes for it to propagate, then retry.",
                            e);
                }

                throw new RuntimeException("Failed to create Google Form (HTTP " + status + "): "
                        + (message != null ? message : "Unknown error"), e);
            }

            throw new RuntimeException("Failed to create Google Form with sections: " + e.getMessage(), e);
        }
    }

    /**
     * Overwrite an existing Google Form by updating its info and replacing all items.
     */
    public void overwriteGoogleForm(Long teacherId, String formId, String title, String description,
                                    List<QuestionnaireItem> questions,
                                    List<QuestionnaireSection> sections) {
        try {
            String accessToken = googleAuthService.getValidAccessToken(teacherId);
            Forms formsService = getFormsService(accessToken);

            // 1. Get the current form to know how many items to delete
            Form form = formsService.forms().get(formId).execute();
            List<Request> batchRequests = new ArrayList<>();

            // 2. Update Form Info (Title and Description)
            Info info = new Info();
            info.setTitle(title);
            if (description != null) {
                info.setDescription(description);
            }
            UpdateFormInfoRequest updateInfo = new UpdateFormInfoRequest()
                    .setInfo(info)
                    .setUpdateMask(description != null ? "title,description" : "title");
            batchRequests.add(new Request().setUpdateFormInfo(updateInfo));

            // 3. Delete all existing items
            if (form.getItems() != null && !form.getItems().isEmpty()) {
                // Delete from highest index down to 0 to avoid index shifting
                for (int i = form.getItems().size() - 1; i >= 0; i--) {
                    DeleteItemRequest deleteReq = new DeleteItemRequest()
                            .setLocation(new Location().setIndex(i));
                    batchRequests.add(new Request().setDeleteItem(deleteReq));
                }
            }

            // Execute the deletion and update info first
            if (!batchRequests.isEmpty()) {
                BatchUpdateFormRequest batchUpdateRequest = new BatchUpdateFormRequest()
                        .setRequests(batchRequests);
                formsService.forms().batchUpdate(formId, batchUpdateRequest).execute();
                log.info("Deleted old items and updated info for form {}", formId);
            }

            // 4. Add the new items using our existing logic
            if ((sections != null && !sections.isEmpty()) || (questions != null && !questions.isEmpty())) {
                addQuestionsAndSectionsToForm(formsService, formId, questions, sections, accessToken);
            }

            log.info("Successfully overwritten Google Form {}", formId);

        } catch (Exception e) {
            log.error("Error overwriting Google Form", e);
            throw new RuntimeException("Failed to sync updates to Google Form: " + e.getMessage(), e);
        }
    }

    /**
     * Fetch a Google Form by id for mapping synchronization.
     */
    public Form getFormById(Long teacherId, String formId) {
        try {
            String accessToken = googleAuthService.getValidAccessToken(teacherId);
            Forms formsService = getFormsService(accessToken);
            return formsService.forms().get(formId).execute();
        } catch (Exception e) {
            log.error("Error fetching Google Form {}", formId, e);
            throw new RuntimeException("Failed to fetch Google Form: " + e.getMessage(), e);
        }
    }

    /**
     * Add questions to an existing Google Form
     */
    private void addQuestionsToForm(Forms formsService, String formId, List<QuestionnaireItem> questions) {
        try {
            List<Request> requests = new ArrayList<>();

            for (int i = 0; i < questions.size(); i++) {
                QuestionnaireItem item = questions.get(i);
                Request request = createQuestionRequest(item, i);
                requests.add(request);
            }

            BatchUpdateFormRequest batchUpdateRequest = new BatchUpdateFormRequest()
                    .setRequests(requests);

            formsService.forms().batchUpdate(formId, batchUpdateRequest).execute();
            log.info("Added {} questions to form {}", questions.size(), formId);

        } catch (Exception e) {
            log.error("Error adding questions to form", e);
            throw new RuntimeException("Failed to add questions to form: " + e.getMessage(), e);
        }
    }

    /**
     * Add questions and sections with page breaks to an existing Google Form
     */
    private void addQuestionsAndSectionsToForm(Forms formsService, String formId, 
                                                List<QuestionnaireItem> looseQuestions, 
                                                List<QuestionnaireSection> sections,
                                                String accessToken) {
        try {
            List<Request> requests = new ArrayList<>();
            int itemIndex = 0;

            // Add loose questions first (if any)
            if (looseQuestions != null && !looseQuestions.isEmpty()) {
                for (QuestionnaireItem item : looseQuestions) {
                    Request request = createQuestionRequest(item, itemIndex++);
                    requests.add(request);
                }
            }

            // Add sections with page breaks
            if (sections != null && !sections.isEmpty()) {
                for (int sectionIdx = 0; sectionIdx < sections.size(); sectionIdx++) {
                    QuestionnaireSection section = sections.get(sectionIdx);

                    // Add page break before section (except before first section if no loose questions)
                    if (itemIndex > 0) {
                        Request pageBreakRequest = createPageBreakRequest(itemIndex++);
                        requests.add(pageBreakRequest);
                    }

                    // Add questions in this section
                    if (section.getItems() != null && !section.getItems().isEmpty()) {
                        List<QuestionnaireItem> sortedItems = new ArrayList<>(section.getItems());
                        sortedItems.sort(java.util.Comparator.comparing(QuestionnaireItem::getOrderIndex));
                        
                        // Check if this is an individual evaluation section
                        boolean isIndividualSection = section.getEvaluateIndividuals() != null && section.getEvaluateIndividuals();
                        
                        for (QuestionnaireItem item : sortedItems) {
                            if (isIndividualSection) {
                                // For individual evaluations, create grid question via direct HTTP call
                                final int NUM_INDIVIDUAL_SLOTS = 10;
                                createGridQuestionViaHttp(formId, item, itemIndex++, NUM_INDIVIDUAL_SLOTS, accessToken);
                            } else {
                                // For non-individual sections, add question normally (once)
                                Request request = createQuestionRequest(item, itemIndex++);
                                requests.add(request);
                            }
                        }
                    }
                }
            }

            if (!requests.isEmpty()) {
                BatchUpdateFormRequest batchUpdateRequest = new BatchUpdateFormRequest()
                        .setRequests(requests);

                formsService.forms().batchUpdate(formId, batchUpdateRequest).execute();
                log.info("Added {} items (questions and page breaks) to form {}", requests.size(), formId);
            }

        } catch (Exception e) {
            log.error("Error adding questions and sections to form", e);
            throw new RuntimeException("Failed to add questions and sections to form: " + e.getMessage(), e);
        }
    }

    /**
     * Create a page break request for Google Forms API
     */
    private Request createPageBreakRequest(int index) {
        Item pageBreakItem = new Item();
        pageBreakItem.setPageBreakItem(new PageBreakItem());

        CreateItemRequest createItemRequest = new CreateItemRequest();
        createItemRequest.setItem(pageBreakItem);
        
        Location location = new Location();
        location.setIndex(index);
        createItemRequest.setLocation(location);

        Request request = new Request();
        request.setCreateItem(createItemRequest);

        return request;
    }

    /**
     * Create a grid question via direct HTTP call to Google Forms API
     * Uses questionGroupItem with rowQuestion objects and shared grid columns
     */
    private void createGridQuestionViaHttp(String formId, QuestionnaireItem item, int index, 
                                          int numRows, String accessToken) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            // Build rows (one rowQuestion for each team member 1-10)
            java.util.List<java.util.Map<String, Object>> questions = new ArrayList<>();
            for (int i = 1; i <= numRows; i++) {
                java.util.Map<String, Object> rowQuestion = new java.util.HashMap<>();
                java.util.Map<String, String> row = new java.util.HashMap<>();
                row.put("title", "Member " + i);
                rowQuestion.put("rowQuestion", row);
                questions.add(rowQuestion);
            }

            // Build grid columns based on question type
            java.util.Map<String, Object> gridColumns = new java.util.HashMap<>();
            java.util.List<java.util.Map<String, String>> options = new ArrayList<>();

            if (item.getQuestionType() == QuestionnaireItem.QuestionType.NUMERIC_SCALE ||
                    item.getQuestionType() == QuestionnaireItem.QuestionType.RATING) {
                // Create numeric scale columns with decimals between highest and 2nd highest
                // e.g., 10 9.9 9.8 9.7 9.6 9.5 9.4 9.3 9.2 9.1 9 8 7 6 5 4 3 2 1
                int low = item.getMinScore() != null ? item.getMinScore() : 1;
                int high = item.getMaxScore() != null ? item.getMaxScore() : 5;
                
                // Add the highest value
                java.util.Map<String, String> option = new java.util.HashMap<>();
                option.put("value", String.valueOf(high));
                options.add(option);
                
                // Add decimal values from high-0.1 to high-0.9
                for (int i = 9; i >= 1; i--) {
                    java.util.Map<String, String> decimalOption = new java.util.HashMap<>();
                    decimalOption.put("value", String.format("%.1f", high - (i / 10.0)));
                    options.add(decimalOption);
                }
                
                // Add remaining integer values
                for (int i = high - 1; i >= low; i--) {
                    java.util.Map<String, String> intOption = new java.util.HashMap<>();
                    intOption.put("value", String.valueOf(i));
                    options.add(intOption);
                }
                gridColumns.put("type", "RADIO");
            } else if (item.getQuestionType() == QuestionnaireItem.QuestionType.MULTIPLE_CHOICE) {
                // Create columns from the multiple choice options
                java.util.Set<String> uniqueChoices = new java.util.LinkedHashSet<>();
                if (item.getChoices() != null && !item.getChoices().isEmpty()) {
                    try {
                        String[] choicesArray = mapper.readValue(item.getChoices(), String[].class);
                        for (String choice : choicesArray) {
                            if (choice != null && !choice.trim().isEmpty()) {
                                uniqueChoices.add(choice.trim());
                            }
                        }
                    } catch (Exception e) {
                        String[] choicesArray = item.getChoices().split(",");
                        for (String choice : choicesArray) {
                            if (choice != null && !choice.trim().isEmpty()) {
                                uniqueChoices.add(choice.trim());
                            }
                        }
                    }
                }
                for (String choice : uniqueChoices) {
                    java.util.Map<String, String> option = new java.util.HashMap<>();
                    option.put("value", choice);
                    options.add(option);
                }
                gridColumns.put("type", "RADIO");
            }
            gridColumns.put("options", options);

            // Build the questionGroupItem
            java.util.Map<String, Object> questionGroupItem = new java.util.HashMap<>();
            questionGroupItem.put("questions", questions);
            questionGroupItem.put("grid", new java.util.HashMap<String, Object>() {{
                put("columns", gridColumns);
            }});

            // Build the item
            java.util.Map<String, Object> item_map = new java.util.HashMap<>();
            item_map.put("title", item.getQuestionText());
            item_map.put("questionGroupItem", questionGroupItem);

            // Build the location
            java.util.Map<String, Object> location = new java.util.HashMap<>();
            location.put("index", index);

            // Build the createItem request
            java.util.Map<String, Object> createItemRequest = new java.util.HashMap<>();
            createItemRequest.put("item", item_map);
            createItemRequest.put("location", location);

            // Build the request
            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("createItem", createItemRequest);

            // Build batch update request
            java.util.List<java.util.Map<String, Object>> requests = new ArrayList<>();
            requests.add(request);

            java.util.Map<String, Object> batchRequest = new java.util.HashMap<>();
            batchRequest.put("requests", requests);

            // Make HTTP request
            String url = "https://forms.googleapis.com/v1/forms/" + formId + ":batchUpdate";
            String jsonBody = mapper.writeValueAsString(batchRequest);
            
            log.info("Creating grid question with JSON: {}", jsonBody);

            com.google.api.client.http.HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            com.google.api.client.http.HttpRequestFactory requestFactory = httpTransport.createRequestFactory(
                    request_inner -> request_inner.setHeaders(
                            new com.google.api.client.http.HttpHeaders().setAuthorization("Bearer " + accessToken)
                    )
            );

            com.google.api.client.http.HttpRequest httpRequest = requestFactory.buildPostRequest(
                    new com.google.api.client.http.GenericUrl(url),
                    com.google.api.client.http.ByteArrayContent.fromString("application/json", jsonBody)
            );

            com.google.api.client.http.HttpResponse response = httpRequest.execute();
            
            if (response.getStatusCode() >= 400) {
                String errorBody = response.parseAsString();
                log.error("Failed to create grid question: HTTP {}: {}", response.getStatusCode(), errorBody);
                throw new RuntimeException("Failed to create grid question: HTTP " + response.getStatusCode() + ": " + errorBody);
            } else {
                log.info("Successfully created grid question for form {}", formId);
            }

        } catch (Exception e) {
            log.error("Error creating grid question via HTTP", e);
            throw new RuntimeException("Failed to create grid question: " + e.getMessage(), e);
        }
    }

    /**
        Item pageBreakItem = new Item();
        pageBreakItem.setPageBreakItem(new PageBreakItem());

        CreateItemRequest createItemRequest = new CreateItemRequest();
        createItemRequest.setItem(pageBreakItem);
        
        Location location = new Location();
        location.setIndex(index);
        createItemRequest.setLocation(location);

        Request request = new Request();
        request.setCreateItem(createItemRequest);

        return request;
    }

    /**
     * Create a grid question for individual student evaluations using direct REST API call
     * Grid has rows for each team member (1-10) and columns based on question type
     */
    private Request createGridQuestion(QuestionnaireItem item, int index, int numRows) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            
            // Build rows (one for each student number)
            java.util.List<java.util.Map<String, String>> rows = new ArrayList<>();
            for (int i = 1; i <= numRows; i++) {
                java.util.Map<String, String> row = new java.util.HashMap<>();
                row.put("title", String.valueOf(i));
                rows.add(row);
            }

            // Build columns based on question type
            java.util.List<java.util.Map<String, String>> columns = new ArrayList<>();

            if (item.getQuestionType() == QuestionnaireItem.QuestionType.NUMERIC_SCALE ||
                    item.getQuestionType() == QuestionnaireItem.QuestionType.RATING) {
                // Create numeric scale columns (e.g., 1-5) - in reverse order for better UX
                int low = item.getMinScore() != null ? item.getMinScore() : 1;
                int high = item.getMaxScore() != null ? item.getMaxScore() : 5;
                for (int i = high; i >= low; i--) {
                    java.util.Map<String, String> col = new java.util.HashMap<>();
                    col.put("title", String.valueOf(i));
                    columns.add(col);
                }
            } else if (item.getQuestionType() == QuestionnaireItem.QuestionType.MULTIPLE_CHOICE) {
                // Create columns from the multiple choice options
                java.util.Set<String> uniqueChoices = new java.util.LinkedHashSet<>();
                if (item.getChoices() != null && !item.getChoices().isEmpty()) {
                    try {
                        String[] choicesArray = mapper.readValue(item.getChoices(), String[].class);
                        for (String choice : choicesArray) {
                            if (choice != null && !choice.trim().isEmpty()) {
                                uniqueChoices.add(choice.trim());
                            }
                        }
                    } catch (Exception e) {
                        String[] choicesArray = item.getChoices().split(",");
                        for (String choice : choicesArray) {
                            if (choice != null && !choice.trim().isEmpty()) {
                                uniqueChoices.add(choice.trim());
                            }
                        }
                    }
                }
                for (String choice : uniqueChoices) {
                    java.util.Map<String, String> col = new java.util.HashMap<>();
                    col.put("title", choice);
                    columns.add(col);
                }
            }

            // Build the grid question JSON structure using standard API format
            java.util.Map<String, Object> gridQuestion = new java.util.HashMap<>();
            gridQuestion.put("rows", rows);
            gridQuestion.put("columns", columns);

            // Build the full question request JSON
            java.util.Map<String, Object> questionJson = new java.util.HashMap<>();
            java.util.Map<String, Object> itemJson = new java.util.HashMap<>();
            java.util.Map<String, Object> questionItem = new java.util.HashMap<>();
            java.util.Map<String, Object> question = new java.util.HashMap<>();
            
            question.put("gridQuestion", gridQuestion);
            question.put("required", item.getRequired() == null || item.getRequired());
            
            questionItem.put("question", question);
            itemJson.put("title", item.getQuestionText());
            itemJson.put("questionItem", questionItem);
            
            java.util.Map<String, Object> location = new java.util.HashMap<>();
            location.put("index", index);
            
            java.util.Map<String, Object> createItemRequest = new java.util.HashMap<>();
            createItemRequest.put("item", itemJson);
            createItemRequest.put("location", location);
            
            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("createItem", createItemRequest);
            
            // Log for debugging
            String gridJson = mapper.writeValueAsString(request);
            log.info("Grid question request JSON: {}", gridJson);
            
            // Convert back to Request object using Jackson
            // Since we can't directly create GridQuestion, we use raw JSON conversion
            Request formRequest = new Request();
            String requestJson = mapper.writeValueAsString(request);
            com.google.api.client.json.JsonParser parser = JSON_FACTORY
                    .createJsonParser(requestJson);
            formRequest = parser.parse(Request.class);
            
            return formRequest;
            
        } catch (Exception e) {
            log.error("Error creating grid question via JSON, falling back to individual questions", e);
            return createQuestionRequest(item, index);
        }
    }


    /**
     * Create a question request based on question type
     */
    private Request createQuestionRequest(QuestionnaireItem item, int index) {
        Question question = new Question();
        // Don't set questionId - Google Forms will auto-generate it

        // Set question text
        question.setRequired(item.getRequired() == null || item.getRequired());

        switch (item.getQuestionType()) {
            case NUMERIC_SCALE:
                ScaleQuestion scaleQuestion = new ScaleQuestion();
                scaleQuestion.setLow(item.getMinScore() != null ? item.getMinScore() : 1);
                scaleQuestion.setHigh(item.getMaxScore() != null ? item.getMaxScore() : 5);
                scaleQuestion.setLowLabel("Low");
                scaleQuestion.setHighLabel("High");
                question.setScaleQuestion(scaleQuestion);
                break;

            case RATING:
                ScaleQuestion ratingQuestion = new ScaleQuestion();
                ratingQuestion.setLow(item.getMinScore() != null ? item.getMinScore() : 1);
                ratingQuestion.setHigh(item.getMaxScore() != null ? item.getMaxScore() : 5);
                question.setScaleQuestion(ratingQuestion);
                break;

            case TEXT:
                TextQuestion textQuestion = new TextQuestion();
                textQuestion.setParagraph(true);
                question.setTextQuestion(textQuestion);
                break;

            case MULTIPLE_CHOICE:
                ChoiceQuestion choiceQuestion = new ChoiceQuestion();
                choiceQuestion.setType("RADIO");
                List<Option> options = new ArrayList<>();

                // Parse choices from JSON or comma-separated string
                java.util.Set<String> uniqueChoices = new java.util.LinkedHashSet<>();
                if (item.getChoices() != null && !item.getChoices().isEmpty()) {
                    try {
                        // Try to parse as JSON array first
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        String[] choicesArray = mapper.readValue(item.getChoices(), String[].class);
                        for (String choice : choicesArray) {
                            if (choice != null && !choice.trim().isEmpty()) {
                                uniqueChoices.add(choice.trim());
                            }
                        }
                    } catch (Exception e) {
                        // If JSON parsing fails, treat as comma-separated
                        String[] choicesArray = item.getChoices().split(",");
                        for (String choice : choicesArray) {
                            if (choice != null && !choice.trim().isEmpty()) {
                                uniqueChoices.add(choice.trim());
                            }
                        }
                    }
                }

                // Convert unique choices to options
                for (String choice : uniqueChoices) {
                    Option option = new Option();
                    option.setValue(choice);
                    options.add(option);
                }

                log.info("Parsed {} options for multiple choice", options.size());
                for (int i = 0; i < options.size(); i++) {
                    log.info("Option {}: {}", i + 1, options.get(i).getValue());
                }

                // If no valid choices found, create default options
                if (options.isEmpty()) {
                    log.warn("No valid choices found, creating default options");
                    for (int i = 1; i <= 3; i++) {
                        Option option = new Option();
                        option.setValue("Option " + i);
                        options.add(option);
                    }
                }

                choiceQuestion.setOptions(options);
                question.setChoiceQuestion(choiceQuestion);
                break;
        }

        // Create the item
        Item formItem = new Item();
        formItem.setTitle(item.getQuestionText());
        formItem.setQuestionItem(new QuestionItem().setQuestion(question));

        // Create the request
        CreateItemRequest createItemRequest = new CreateItemRequest();
        createItemRequest.setItem(formItem);
        Location location = new Location();
        location.setIndex(index);
        createItemRequest.setLocation(location);

        Request request = new Request();
        request.setCreateItem(createItemRequest);

        return request;
    }

    /**
     * Get form responses
     */
    public List<FormResponse> getFormResponses(Long teacherId, String formId) {
        try {
            String accessToken = googleAuthService.getValidAccessToken(teacherId);
            Forms formsService = getFormsService(accessToken);

            ListFormResponsesResponse response = formsService.forms().responses()
                    .list(formId)
                    .execute();

            return response.getResponses() != null ? response.getResponses() : new ArrayList<>();

        } catch (Exception e) {
            log.error("Error fetching form responses", e);
            throw new RuntimeException("Failed to fetch form responses: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a Google Form
     */
    public void deleteGoogleForm(Long teacherId, String formId) {
        try {
            String accessToken = googleAuthService.getValidAccessToken(teacherId);
            getFormsService(accessToken);

            // Note: Google Forms API doesn't have a direct delete method
            // We would need to use Drive API to move to trash
            log.warn("Google Forms API doesn't support direct deletion. Form {} should be deleted via Drive API",
                    formId);

        } catch (Exception e) {
            log.error("Error deleting Google Form", e);
            throw new RuntimeException("Failed to delete Google Form: " + e.getMessage(), e);
        }
    }

    /**
     * Create Forms service with OAuth credentials
     */
    private Forms getFormsService(String accessToken) throws Exception {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(accessToken, new Date(System.currentTimeMillis() + 3600000)));

        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Forms.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
