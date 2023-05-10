package com.example.coursebuilder.demo.services;

import com.example.coursebuilder.demo.QuizQuestion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TranscriptionService {
    private static final String GPT3_API_ENDPOINT = "https://api.openai.com/v1/completions";
    private static final String WHISPER_API_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";
    @Value("${openai.apiKey}")
    private String apiKey;

    public String transcribeVideo(File videoFile) throws IOException, InterruptedException {
        // Extract audio from the video and save as an mp3 file
        File audioFile = extractAudioFromVideo(videoFile);

        // Call the OpenAI Whisper API to get a transcript and save locally
        String transcript = transcribeAudioWithWhisperAPI(audioFile);
        return transcript;
    }

    private File extractAudioFromVideo(File videoFile) throws IOException, InterruptedException {

        // Create the "Downloads/temp" folder if it doesn't exist
        File downloadsFolder = new File(System.getProperty("user.home"), "Downloads");
        File tempFolder = new File(downloadsFolder, "temp");
        if (!tempFolder.exists()) {
            tempFolder.mkdirs();
        }

        // Create a temporary file for the extracted audio in the "Downloads/temp" folder
        File tempAudioFile = File.createTempFile("temp-audio-", ".mp3", tempFolder);
        // Run the ffmpeg command to extract the audio from the video and save as an mp3 file
        System.out.println("Video file path during extract Audio :" + videoFile.getAbsolutePath());
        ProcessBuilder processBuilder = new ProcessBuilder("ffmpeg", "-i", videoFile.getAbsolutePath(), "-vn", "-acodec", "libmp3lame", "-ac", "2", "-ab", "64k", "-ar", "48000", tempAudioFile.getAbsolutePath());
        Process process = processBuilder.start();
        process.waitFor(10000, TimeUnit.MILLISECONDS);
        /*Wait for 30 seconds
        System.out.println("Waiting for 30 seconds...");
        Thread.sleep(30000);
        //Terminate the process if it's still running
         if (process.isAlive()) {
            process.destroy();
            System.err.println("Process terminated forcefully!");
        }*/
        System.out.println("Audio file generated - name: " + tempAudioFile.getName());
        System.out.println("Audio file location: " + tempAudioFile.getAbsolutePath());
        return tempAudioFile;
    }


    private String transcribeAudioWithWhisperAPI(File audioFile) throws IOException {
        System.out.println("Temp Audio file being sent to Whisper API - name: " + audioFile.getName());
        System.out.println("Temp Audio file being sent to Whisper API location : " + audioFile.getAbsolutePath());
        File testAudioFile = new File("/Users/rbhaumik/downloads/temp/newtons-laws-audio.mp3");
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // Set the connect timeout
                .readTimeout(30, TimeUnit.SECONDS) // Set the read timeout
                .build();
        String mimeType = Files.probeContentType(audioFile.toPath());
        System.out.println("Audio file MIME type: " + mimeType);

        // Create the request body
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", testAudioFile.getName(), RequestBody.create(MediaType.parse("audio/mpeg"), testAudioFile))
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "srt")
                .build();

        // Create the request
        Request request = new Request.Builder()
                .url(WHISPER_API_ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        // Execute the request and get the response
        Response response = client.newCall(request).execute();

        // Check if the request was successful
        if (response.isSuccessful()) {
            // Get the response body as a byte array
            byte[] responseBodyBytes = response.body().bytes();

            // Define the file path where you want to save the SRT file
            String srtFilePath = "/Users/rbhaumik/downloads/temp/" + audioFile.hashCode() + ".srt";

            // Write the response body bytes to the SRT file
            FileOutputStream fos = new FileOutputStream(srtFilePath);
            fos.write(responseBodyBytes);
            fos.close();
            System.out.println("SRT file saved successfully at: " + srtFilePath);

            // Read the contents of the SRT file and append it to the transcriptBuilder
            String srtContent = new String(responseBodyBytes);
            StringBuilder transcriptBuilder = new StringBuilder();
            transcriptBuilder.append(srtContent);

            // Close the response body
            response.body().close();

            // Return the transcript
            return transcriptBuilder.toString();
        } else {
            // Handle the unsuccessful response here
            System.out.println("Error: " + response.code() + " " + response.message());
        }

        // Return null or an appropriate value if an error occurs
        return null;
    }


    public String createCourseSummary(String transcript) throws IOException {
        String summary = "placeholder for summary";
        // Call the OpenAI GPT-3 API to create a course summary based on the transcript
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // Set the connect timeout
                .readTimeout(30, TimeUnit.SECONDS) // Set the read timeout
                .build();
        MediaType mediaType = MediaType.parse("application/json");

        JsonObject requestBodyJson = new JsonObject();
        requestBodyJson.addProperty("model", "text-davinci-003");
        requestBodyJson.addProperty("prompt", "Extract a summary from the transcript below\n" + transcript);
        requestBodyJson.addProperty("temperature", 0.5);
        requestBodyJson.addProperty("max_tokens", 60);
        requestBodyJson.addProperty("n", 3);

        RequestBody requestBody = RequestBody.create(mediaType, requestBodyJson.toString());

        Request request = new Request.Builder()
                .url(GPT3_API_ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            int responseCode = response.code(); // Get the response code
            String responseBody = response.body().string(); // Get the response body

            if (response.isSuccessful()) {
                System.out.println("Da-vinci response for summary: " + responseBody);

                // Parse the response JSON
                JsonObject responseJson = JsonParser.parseString(responseBody).getAsJsonObject();

                // Extract the summary text from the API response
                JsonArray choices = responseJson.getAsJsonArray("choices");
                if (choices != null && choices.size() > 0) {
                    JsonObject choice = choices.get(0).getAsJsonObject();
                    summary = choice.get("text").getAsString();
                    // Remove leading and trailing whitespace from the summary text
                    summary = summary.trim();
                }
            } else {
                System.out.println("Da-vinci API request failed with response code: " + responseCode);
                System.out.println("Response body: " + responseBody);
            }
        }

        return summary;
    }

    public List<QuizQuestion> createQuiz(String transcript) throws IOException {
        List<QuizQuestion> quizQuestions = new ArrayList<>();

        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(40))
                .build();

        MediaType mediaType = MediaType.parse("application/json");

        JsonObject requestBodyJson = new JsonObject();
        String prompt = "Create a multiple choice quiz (with at least 2 questions and at most 5 questions) each question must be different, do it in the format shown below:\n"
                + "\n\nQ. [Question Text]\n"
                + "A. [Option A]\n"
                + "B. [Option B]\n"
                + "C. [Option C]\n"
                + "D. [Option D]\n"
                + "Correct option: [Answer Index]\n"
                + "for the transcript pasted below\n"
                + transcript;
        requestBodyJson.addProperty("model", "text-davinci-003");
        requestBodyJson.addProperty("prompt", prompt);
        requestBodyJson.addProperty("temperature", 0.5);
        requestBodyJson.addProperty("max_tokens", 60);
        requestBodyJson.addProperty("n", 3);

        RequestBody requestBody = RequestBody.create(mediaType, requestBodyJson.toString());

        Request request = new Request.Builder()
                .url(GPT3_API_ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                System.out.println("Da-vinci response for Quiz Questions: " + responseBody);

                // Parse the response to get the quiz questions
                JsonParser parser = new JsonParser();
                JsonObject responseObject = parser.parse(responseBody).getAsJsonObject();
                if (responseObject.has("choices") && responseObject.get("choices").isJsonArray()) {
                    JsonArray jsonArray = responseObject.getAsJsonArray("choices");
                    for (JsonElement element : jsonArray) {
                        JsonObject jsonObject = element.getAsJsonObject();
                        String question = jsonObject.get("text").getAsString();

                        // Extract question, options, and correct answer from the question text
                        String[] questionParts = question.split("\n");
                        String questionText = questionParts[0].substring(3).trim(); // Remove "Q. " prefix

                        List<String> options = extractOptions(questionParts);
                        int correctAnswerIndex = extractCorrectAnswerIndex(questionParts);

                        quizQuestions.add(new QuizQuestion(questionText, options, correctAnswerIndex));
                    }

                } else {
                    // Handle the case where the choices field is missing or not an array
                }
            }
        } catch (IOException e) {
            // Handle the exception, e.g., log the error or throw a custom exception
            System.out.println("Exception - " + e.getMessage());
        }

        return quizQuestions;
    }

    private List<String> extractOptions(String[] questionParts) {
        List<String> options = new ArrayList<>();

        for (String part : questionParts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith("A.") || trimmedPart.startsWith("B.") ||
                    trimmedPart.startsWith("C.") || trimmedPart.startsWith("D.")) {
                options.add(trimmedPart.substring(3).trim());
            }
        }

        return options;
    }

    private int extractCorrectAnswerIndex(String[] questionParts) {
        int correctAnswerIndex = -1;
        String correctOptionPrefix = "Correct option:";

        for (String part : questionParts) {
            String trimmedPart = part.trim();
            if (trimmedPart.startsWith(correctOptionPrefix)) {
                String correctOption = trimmedPart.substring(correctOptionPrefix.length()).trim();
                correctAnswerIndex = extractOptionIndex(correctOption);
            }
        }

        return correctAnswerIndex;
    }

    private int extractOptionIndex(String option) {
        Pattern pattern = Pattern.compile("^[A-Z]");
        Matcher matcher = pattern.matcher(option);
        if (matcher.find()) {
            String optionIndex = matcher.group();
            return optionIndex.charAt(0) - 'A';
        } else {
            return -1;
        }
    }

    public List<String> createKeyTakeaways(String transcript) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS) // Set the connect timeout
                .readTimeout(30, TimeUnit.SECONDS) // Set the read timeout
                .build();
        MediaType mediaType = MediaType.parse("application/json");

        JsonObject requestBodyJson = new JsonObject();
        requestBodyJson.addProperty("model", "text-davinci-003");
        requestBodyJson.addProperty("prompt", "Can you extract a list of important takeaways from the transcript below" +
                "ensure each point is unique and avoids repetition and do not use a numbered list:\n" + transcript);
        requestBodyJson.addProperty("temperature", 0.5);
        requestBodyJson.addProperty("max_tokens", 60);
        requestBodyJson.addProperty("n", 3);

        RequestBody requestBody = RequestBody.create(mediaType, requestBodyJson.toString());

        Request request = new Request.Builder()
                .url(GPT3_API_ENDPOINT)
                .header("Authorization", "Bearer " + apiKey)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                System.out.println("Da-vinci response for key takeaways: " + responseBody);

                JsonParser parser = new JsonParser();
                // Parse the response to get the key takeaways
                JsonObject responseObject = parser.parse(responseBody).getAsJsonObject();

                if (responseObject.has("choices") && responseObject.get("choices").isJsonArray()) {
                    JsonArray jsonArray = responseObject.getAsJsonArray("choices");
                    List<String> keyTakeaways = new ArrayList<>();
                    for (JsonElement element : jsonArray) {
                        JsonObject jsonObject = element.getAsJsonObject();
                        String keyTakeaway = jsonObject.get("text").getAsString();
                        keyTakeaways.add(keyTakeaway);
                    }
                    return keyTakeaways;
                } else {
                    // Handle the case where the choices field is missing or not an array
                }
            }
        }
        // Add appropriate error handling or return statement in case response is not successful
        return Collections.emptyList();
    }
}

