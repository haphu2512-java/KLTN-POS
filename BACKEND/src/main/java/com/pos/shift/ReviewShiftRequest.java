package com.pos.shift;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewShiftRequest {

    /** Cách xử lý chênh lệch, vd. "Trừ lương 6.000đ" hoặc "Bỏ qua, nhầm tiền thối" */
    @NotBlank(message = "Ghi cách xử lý chênh lệch")
    @Size(max = 500, message = "Ghi chú duyệt tối đa 500 ký tự")
    private String note;
}
