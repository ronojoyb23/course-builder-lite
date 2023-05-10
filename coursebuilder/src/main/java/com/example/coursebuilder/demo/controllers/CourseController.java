package com.example.coursebuilder.demo.controllers;

import com.example.coursebuilder.demo.QuizQuestion;
import com.example.coursebuilder.demo.services.TranscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Controller
public class CourseController {

    @Autowired
    private TranscriptionService transcriptionService;

    @GetMapping("/")
    public String showCourseBuilderForm(Model model) {
        model.addAttribute("pageTitle", "Course Builder");
        return "index";
    }

    @PostMapping("/upload")
    public String handleFileUpload(@RequestParam("file") MultipartFile file, Model model) throws InterruptedException {
        try {
            // Save the uploaded file to disk
            File videoFile = saveFile(file);
            System.out.println("VideoFile name is " + videoFile.getName());
            // Call the TranscriptionService to get a transcript of the video
            String transcript = transcriptionService.transcribeVideo(videoFile);
            //System.out.println("Transcript: " + transcript);
            // Call the TranscriptionService to create a course summary based on the transcript
            String courseSummary = transcriptionService.createCourseSummary(transcript);
            System.out.println("Summary: " + courseSummary);
            // Call the TranscriptionService to create a quiz based on the transcript
            List<QuizQuestion> quizQuestions = transcriptionService.createQuiz(transcript);

            // Iterate over the list and print out the contents of each QuizQuestion
            for (QuizQuestion question : quizQuestions) {
                System.out.println("Question: " + question.getQuestionText());
                System.out.println("Options: " + question.getOptions());
                System.out.println("Answer Index: " + question.getAnswerIndex());
                System.out.println();
            }

            // Call the TranscriptionService to create key takeaways based on the transcript
            List<String> keyTakeaways = transcriptionService.createKeyTakeaways(transcript);
            // Counter variable to maintain sequential numbering
            int counter = 1;

            /*
            for (String keyTakeaway : keyTakeaways) {
                System.out.println(+ counter + ": " + keyTakeaway);
                counter++;
            }*/
            // Add the results to the model
            model.addAttribute("pageTitle", "Course Results");
            model.addAttribute("summary", courseSummary);
            model.addAttribute("quizQuestions", quizQuestions);
            model.addAttribute("keyTakeaways", keyTakeaways);
            return "results";
        } catch (IOException e) {
            model.addAttribute("errorMessage", "Error uploading file: " + e.getMessage());
            return "index";
        }
    }

    private File saveFile(MultipartFile file) throws IOException {
        String fileName = StringUtils.cleanPath(file.getOriginalFilename());
        File tempFolder = new File(System.getProperty("user.home") + "/Downloads/temp");
        tempFolder.mkdirs();
        File tempFile = new File(tempFolder, "video-" + fileName);
        file.transferTo(tempFile);
        return tempFile;
    }

}
