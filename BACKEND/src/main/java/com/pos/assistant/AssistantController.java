package com.pos.assistant;

import com.pos.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("isAuthenticated()")
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
@Tag(name = "Assistant", description = "Trợ lý hỏi đáp số liệu cửa hàng cho nhân viên (dựa trên luật, không dùng LLM)")
public class AssistantController {

    private final AssistantService assistantService;

    @PostMapping("/chat")
    @Operation(summary = "Hỏi trợ lý: doanh thu, bán chạy, tồn kho, hàng sắp hết, gợi ý nhập (theo vai trò và chi nhánh đang đăng nhập)")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(assistantService.chat(request.getMessage())));
    }
}
