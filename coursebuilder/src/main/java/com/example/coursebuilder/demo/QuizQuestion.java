package com.example.coursebuilder.demo;

import java.util.List;

public class QuizQuestion {
    private String questionText;
    private List<String> options;
    private List<String> answerOptions; // New property
    private int answerIndex;

    public QuizQuestion(String questionText, List<String> options, int answerIndex) {
        this.questionText = questionText;
        this.options = options;
        this.answerIndex = answerIndex;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getAnswerIndex() {
        return answerIndex;
    }

    public void setAnswerIndex(int answerIndex) {
        this.answerIndex = answerIndex;
    }
}
