package com.aarushi.qa.dto;
import java.util.List;
public record AskResponse(String answer,boolean refused,List<Citation> citations) {}