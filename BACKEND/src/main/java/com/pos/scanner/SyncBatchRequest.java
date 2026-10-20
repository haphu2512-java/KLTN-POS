package com.pos.scanner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncBatchRequest {

    @NotNull(message = "Danh sách mã quét không được để trống")
    @Size(max = 200, message = "Mỗi lần đồng bộ tối đa 200 mã quét")
    private List<@Valid BarcodeScanRequest> scans;
}
