package com.aarushi.qa.controller;
import com.aarushi.qa.dto.*; import com.aarushi.qa.rag.RagService; import jakarta.validation.Valid; import org.springframework.http.MediaType; import org.springframework.web.bind.annotation.*; import reactor.core.publisher.Flux; import java.time.Duration;
@RestController @RequestMapping("/api/qa") public class QuestionController {
 private final RagService rag; public QuestionController(RagService rag){this.rag=rag;}
 @PostMapping public AskResponse ask(@Valid @RequestBody AskRequest r){return rag.ask(r.tenantId(),r.question());}
 @PostMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE) public Flux<String> stream(@Valid @RequestBody AskRequest r){
  String answer=rag.ask(r.tenantId(),r.question()).answer(); return Flux.fromArray(answer.split(" ")).map(x->x+" ").delayElements(Duration.ofMillis(15));}
}