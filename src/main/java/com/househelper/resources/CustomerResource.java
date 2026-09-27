package com.househelper.resources;

import com.househelper.dto.CustomerRequest;
import com.househelper.dto.CustomerResponse;
import com.househelper.dto.BookingResponse;
import com.househelper.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customers", description = "Manage customers who request house-help services.")
public class CustomerResource {

    private final CustomerService customerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a customer", description = "Creates a customer and returns the generated customer ID.")
    public CustomerResponse createCustomer(@Valid @RequestBody CustomerRequest request) {
        return customerService.createCustomer(request);
    }

    @GetMapping
    @Operation(summary = "List customers", description = "Returns all registered customers.")
    public List<CustomerResponse> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get a customer", description = "Returns one customer by its generated ID.")
    public CustomerResponse getCustomer(@PathVariable UUID customerId) {
        return customerService.getCustomer(customerId);
    }

    @GetMapping("/{customerId}/bookings")
    @Operation(summary = "List a customer's bookings",
            description = "Returns the customer's bookings, including cancelled bookings and recurring series IDs.")
    public List<BookingResponse> getCustomerBookings(@PathVariable UUID customerId) {
        return customerService.getCustomerBookings(customerId);
    }

    @PutMapping("/{customerId}")
    @Operation(summary = "Replace customer details", description = "Updates the customer's name and service address.")
    public CustomerResponse updateCustomer(@PathVariable UUID customerId,
                                           @Valid @RequestBody CustomerRequest request) {
        return customerService.updateCustomer(customerId, request);
    }

    @DeleteMapping("/{customerId}")
    @Operation(summary = "Delete a customer", description = "Deletes a customer only when it has no booking history.")
    public ResponseEntity<Void> deleteCustomer(@PathVariable UUID customerId) {
        customerService.deleteCustomer(customerId);
        return ResponseEntity.noContent().build();
    }
}
