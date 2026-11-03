package com.pos.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Hãy nhập câu hỏi")
    @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự")
    private String message;
}
