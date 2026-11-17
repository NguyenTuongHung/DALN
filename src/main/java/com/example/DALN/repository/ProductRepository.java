
package com.example.DALN.repository;

import com.example.DALN.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // Lấy sản phẩm đang bán, mới đăng trước
    List<Product> findByStatusIgnoreCaseOrderByCreatedAtDesc(String status);

    // Tìm sản phẩm theo danh mục
    List<Product> findBySportTypeIgnoreCaseAndStatusIgnoreCaseOrderByCreatedAtDesc(
            String sportType, String status
    );
}
