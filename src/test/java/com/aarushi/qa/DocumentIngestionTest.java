package com.aarushi.qa;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class DocumentIngestionTest {@Test void onlyPdfIsAccepted(){assertTrue("a.pdf".endsWith(".pdf"));assertFalse("a.txt".endsWith(".pdf"));}}
