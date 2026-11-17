package com.pos.assistant;

import java.util.Optional;

/**
 * Lớp dự phòng khi bộ luật ({@link IntentParser}) không hiểu câu hỏi: nhờ mô hình ngôn ngữ đoán câu hỏi thuộc
 * loại nào trong {@link ChatIntent}. Mô hình chỉ phân loại, không trả lời, nên số liệu luôn do backend tính.
 */
public interface LlmIntentClassifier {

    /** Tên nguồn ghi vào {@link ChatResponse#getSource()} khi câu hỏi được hiểu nhờ lớp này */
    String sourceName();

    /** Rỗng khi tắt, chưa cấu hình, lỗi mạng hoặc mô hình cũng không hiểu */
    Optional<ParsedQuestion> classify(String message);
}
