package com.aarushi.qa.exception;
import org.springframework.http.HttpStatus; import org.springframework.web.bind.annotation.*; import java.util.Map;
@RestControllerAdvice public class GlobalExceptionHandler {
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST)
 public Map<String,Object> bad(IllegalArgumentException e){return Map.of("error","BAD_REQUEST","message",e.getMessage());}
 @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
 public Map<String,Object> internal(Exception e){return Map.of("error","INTERNAL_SERVER_ERROR","message","Unexpected server error");}
}