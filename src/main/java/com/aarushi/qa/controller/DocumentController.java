package com.aarushi.qa.controller;
import com.aarushi.qa.dto.DocumentResponse; import com.aarushi.qa.service.DocumentService; import jakarta.validation.constraints.NotBlank; import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.*; import org.springframework.web.multipart.MultipartFile; import java.io.IOException; import java.util.UUID;
@RestController @RequestMapping("/api/documents") public class DocumentController {
 private final DocumentService service; public DocumentController(DocumentService service){this.service=service;}
 @PostMapping public ResponseEntity<DocumentResponse> upload(@RequestParam @NotBlank String tenantId,@RequestPart MultipartFile file)throws IOException{return ResponseEntity.accepted().body(service.upload(tenantId,file));}
 @GetMapping("/{id}") public DocumentResponse get(@PathVariable UUID id,@RequestParam @NotBlank String tenantId){return service.get(id,tenantId);}
}