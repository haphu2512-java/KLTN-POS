package com.pos.purchase;

import com.pos.common.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {

    private final SupplierRepository supplierRepository;

    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new BusinessException("SUPPLIER_NOT_FOUND", "Không tìm thấy nhà cung cấp với ID: " + id));
    }

    @Transactional
    public Supplier createSupplier(Supplier supplier) {
        supplier.setId(null);
        return supplierRepository.save(supplier);
    }
}
