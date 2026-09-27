package com.househelper.repository;

import com.househelper.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
}
