package com.pos.assistant;

/**
 * Kết quả phân tích câu hỏi.
 *
 * @param intent    ý định
 * @param days      số ngày của khoảng thời gian (1 = hôm nay)
 * @param yesterday true nếu hỏi riêng ngày hôm qua
 * @param keyword   tên/mã sản phẩm cần tra (giữ nguyên dấu tiếng Việt), có thể rỗng
 */
public record ParsedQuestion(ChatIntent intent, int days, boolean yesterday, String keyword) {
}
