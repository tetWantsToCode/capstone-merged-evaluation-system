package group9.advisor_eval_system.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleFormsService {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String APPLICATION_NAME = "Adviser Evaluation System";

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
            throw new RuntimeException("Failed to create Google Form: " + e.getMessage(), e);
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
     * Create a question request based on question type
     */
    private Request createQuestionRequest(QuestionnaireItem item, int index) {
        Question question = new Question();
        // Don't set questionId - Google Forms will auto-generate it

        // Set question text
        question.setRequired(true);

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
            Forms formsService = getFormsService(accessToken);

            // Note: Google Forms API doesn't have a direct delete method
            // We would need to use Drive API to move to trash
            log.warn("Google Forms API doesn't support direct deletion. Form {} should be deleted via Drive API", formId);

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
                new AccessToken(accessToken, new Date(System.currentTimeMillis() + 3600000))
        );
        
        HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(credentials);

        return new Forms.Builder(httpTransport, JSON_FACTORY, requestInitializer)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
