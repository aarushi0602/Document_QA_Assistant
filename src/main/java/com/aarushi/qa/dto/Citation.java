package com.aarushi.qa.dto;
public record Citation(String documentId,String filename,Integer pageNumber,Integer chunkIndex,double similarity) {}