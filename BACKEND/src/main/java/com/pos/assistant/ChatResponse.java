package com.pos.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /** Câu trả lời dạng văn bản, mỗi ý một dòng */
    private String reply;

    /** Ý định đã nhận ra (xem ChatIntent) */
    private String intent;

    /** Nguồn câu trả lời; hiện chỉ có RULE (trả lời từ dữ liệu theo mẫu câu, không gọi LLM) */
    private String source;

    /** Câu hỏi gợi ý để người dùng bấm hỏi tiếp */
    private List<String> suggestions;
}
