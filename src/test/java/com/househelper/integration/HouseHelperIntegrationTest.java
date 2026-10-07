package com.househelper.integration;

import com.househelper.dto.AvailabilityRequest;
import com.househelper.dto.CustomerRequest;
import com.househelper.dto.CustomerResponse;
import com.househelper.dto.CustomerReviewRequest;
import com.househelper.dto.CustomerReviewResponse;
import com.househelper.dto.EmergencyCancelResponse;
import com.househelper.dto.HelperEmergencyCancelRequest;
import com.househelper.dto.HelperOnboardRequest;
import com.househelper.dto.HelperSearchResponse;
import com.househelper.dto.PaymentStatusUpdateRequest;
import com.househelper.dto.UnifiedBookingRequest;
import com.househelper.model.AvailabilityStatus;
import com.househelper.model.BookingStatus;
import com.househelper.model.BookingType;
import com.househelper.model.Gender;
import com.househelper.model.Payment;
import com.househelper.model.PaymentMethod;
import com.househelper.model.PaymentStatus;
import com.househelper.model.PaymentType;
import com.househelper.model.SkillType;
import com.househelper.repository.PaymentRepository;
import com.househelper.service.booking.BookingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class HouseHelperIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private PaymentRepository paymentRepository;

    @BeforeEach
    void setUp() {
        restTemplate.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    @DisplayName("End-to-End: Onboard helper, add availability, create customer, book multi-hour, pay, complete, and maid reviews customer")
    void endToEndHappyPathWithCustomerReview() {
        // 1. Onboard Helper
        HelperOnboardRequest helperReq = HelperOnboardRequest.builder()
                .name("Sunita Sharma")
                .phone("9876543201")
                .gender(Gender.FEMALE)
                .localities(Set.of("Koramangala", "Indiranagar"))
                .skills(Set.of(SkillType.CLEANING, SkillType.COOKING))
                .hourlyRate(300.0)
                .governmentIdProof("GOV-ID-12345")
                .build();

        ResponseEntity<HelperSearchResponse> onboardRes = restTemplate.postForEntity(
                "/api/helpers", helperReq, HelperSearchResponse.class);
        assertEquals(HttpStatus.OK, onboardRes.getStatusCode());
        assertNotNull(onboardRes.getBody());
        UUID helperId = onboardRes.getBody().getId();

        // 2. Add 2 consecutive hourly availability slots for tomorrow
        LocalDate bookingDate = LocalDate.now().plusDays(2);
        List<AvailabilityRequest> availReq = List.of(
                AvailabilityRequest.builder()
                        .slotDate(bookingDate)
                        .startTime(LocalTime.of(10, 0))
                        .endTime(LocalTime.of(11, 0))
                        .status(AvailabilityStatus.AVAILABLE)
                        .build(),
                AvailabilityRequest.builder()
                        .slotDate(bookingDate)
                        .startTime(LocalTime.of(11, 0))
                        .endTime(LocalTime.of(12, 0))
                        .status(AvailabilityStatus.AVAILABLE)
                        .build()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<AvailabilityRequest>> availEntity = new HttpEntity<>(availReq, headers);

        ResponseEntity<Map> availRes = restTemplate.exchange(
                "/api/helpers/" + helperId + "/availability",
                HttpMethod.PUT,
                availEntity,
                Map.class);
        assertEquals(HttpStatus.OK, availRes.getStatusCode());
        assertEquals(2, availRes.getBody().get("slotsUpdated"));

        // 3. Register Customer with phone & email
        CustomerRequest custReq = new CustomerRequest("Aman Verma", "100 Feet Rd, Indiranagar", "9123456780", "aman@example.com");
        ResponseEntity<CustomerResponse> custRes = restTemplate.postForEntity(
                "/api/customers", custReq, CustomerResponse.class);
        assertEquals(HttpStatus.CREATED, custRes.getStatusCode());
        assertNotNull(custRes.getBody());
        UUID customerId = custRes.getBody().getId();
        assertEquals("Aman Verma", custRes.getBody().getName());
        assertEquals("9123456780", custRes.getBody().getPhone());

        // 4. Create 2-hour Scheduled Booking
        UnifiedBookingRequest bookingReq = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(customerId)
                .locality("Indiranagar")
                .skill(SkillType.CLEANING)
                .bookingDate(bookingDate)
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .paymentMethod(PaymentMethod.UPI)
                .build();

        ResponseEntity<BookingResult> bookingRes = restTemplate.postForEntity(
                "/api/bookings", bookingReq, BookingResult.class);
        assertEquals(HttpStatus.CREATED, bookingRes.getStatusCode());
        assertNotNull(bookingRes.getBody());
        UUID bookingId = bookingRes.getBody().getId();
        UUID paymentId = bookingRes.getBody().getBookings().get(0).getPaymentId();
        assertEquals(600.0, bookingRes.getBody().getTotalAmount()); // 2 hours * 300.0
        assertEquals(BookingStatus.PENDING_PAYMENT, bookingRes.getBody().getBookings().get(0).getStatus());

        // 5. Complete Payment
        PaymentStatusUpdateRequest paymentUpdate = new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS);
        HttpEntity<PaymentStatusUpdateRequest> paymentEntity = new HttpEntity<>(paymentUpdate, headers);
        ResponseEntity<Map> paymentRes = restTemplate.exchange(
                "/api/payments/" + paymentId + "/status",
                HttpMethod.PATCH,
                paymentEntity,
                Map.class);
        assertEquals(HttpStatus.OK, paymentRes.getStatusCode());

        // Verify booking confirmed
        ResponseEntity<Map> confirmedBookingRes = restTemplate.getForEntity(
                "/api/bookings/" + bookingId, Map.class);
        assertEquals(HttpStatus.OK, confirmedBookingRes.getStatusCode());
        assertEquals("CONFIRMED", confirmedBookingRes.getBody().get("status"));

        // 6. Complete Service Delivery
        ResponseEntity<Map> completeRes = restTemplate.postForEntity(
                "/api/bookings/" + bookingId + "/complete", null, Map.class);
        assertEquals(HttpStatus.OK, completeRes.getStatusCode());
        assertEquals("COMPLETED", completeRes.getBody().get("status"));

        // 7. Maid leaves review for the Customer
        CustomerReviewRequest reviewReq = CustomerReviewRequest.builder()
                .bookingId(bookingId)
                .helperId(helperId)
                .rating(5)
                .review("Punctual and very respectful customer!")
                .build();

        ResponseEntity<CustomerReviewResponse> reviewRes = restTemplate.postForEntity(
                "/api/customers/" + customerId + "/reviews", reviewReq, CustomerReviewResponse.class);
        assertEquals(HttpStatus.CREATED, reviewRes.getStatusCode());
        assertNotNull(reviewRes.getBody());
        assertEquals(5, reviewRes.getBody().getRating());
        assertEquals("Punctual and very respectful customer!", reviewRes.getBody().getReview());

        // 8. Verify Customer's rating aggregation
        ResponseEntity<CustomerResponse> updatedCustRes = restTemplate.getForEntity(
                "/api/customers/" + customerId, CustomerResponse.class);
        assertEquals(HttpStatus.OK, updatedCustRes.getStatusCode());
        assertEquals(5.0, updatedCustRes.getBody().getRating());
        assertEquals(1L, updatedCustRes.getBody().getRatingCount());
    }

    @Test
    @DisplayName("End-to-End: Helper Emergency Cancellation triggers automatic substitute reassignment in queue")
    void helperEmergencyCancellationWithSuccessfulSubstituteReassignment() {
        LocalDate bookingDate = LocalDate.now().plusDays(3);
        LocalTime startTime = LocalTime.of(14, 0);
        LocalTime endTime = LocalTime.of(15, 0);

        // 1. Onboard Helper 1 (Original Helper - Rate 250)
        HelperOnboardRequest h1Req = HelperOnboardRequest.builder()
                .name("Kavita Devi")
                .phone("9876500001")
                .gender(Gender.FEMALE)
                .localities(Set.of("Whitefield"))
                .skills(Set.of(SkillType.COOKING))
                .hourlyRate(250.0)
                .governmentIdProof("PROOF-1")
                .build();
        UUID h1Id = restTemplate.postForEntity("/api/helpers", h1Req, HelperSearchResponse.class).getBody().getId();

        // 2. Onboard Helper 2 (Substitute Candidate - Rate 350)
        HelperOnboardRequest h2Req = HelperOnboardRequest.builder()
                .name("Meena Kumari")
                .phone("9876500002")
                .gender(Gender.FEMALE)
                .localities(Set.of("Whitefield"))
                .skills(Set.of(SkillType.COOKING))
                .hourlyRate(350.0)
                .governmentIdProof("PROOF-2")
                .build();
        UUID h2Id = restTemplate.postForEntity("/api/helpers", h2Req, HelperSearchResponse.class).getBody().getId();

        // 3. Add availability for both helpers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<AvailabilityRequest> slotReq = List.of(AvailabilityRequest.builder()
                .slotDate(bookingDate).startTime(startTime).endTime(endTime).status(AvailabilityStatus.AVAILABLE).build());

        restTemplate.exchange("/api/helpers/" + h1Id + "/availability", HttpMethod.PUT, new HttpEntity<>(slotReq, headers), Map.class);
        restTemplate.exchange("/api/helpers/" + h2Id + "/availability", HttpMethod.PUT, new HttpEntity<>(slotReq, headers), Map.class);

        // 4. Register Customer & Book Helper 1
        UUID customerId = restTemplate.postForEntity("/api/customers",
                new CustomerRequest("Rohit Sen", "Whitefield Main Rd"), CustomerResponse.class).getBody().getId();

        UnifiedBookingRequest bookingReq = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(customerId)
                .locality("Whitefield")
                .skill(SkillType.COOKING)
                .bookingDate(bookingDate)
                .startTime(startTime)
                .endTime(endTime)
                .paymentMethod(PaymentMethod.CARD)
                .build();

        BookingResult bookingResult = restTemplate.postForEntity("/api/bookings", bookingReq, BookingResult.class).getBody();
        UUID bookingId = bookingResult.getId();
        UUID paymentId = bookingResult.getBookings().get(0).getPaymentId();

        // Pay for booking
        restTemplate.exchange("/api/payments/" + paymentId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS), headers), Map.class);

        // Verify booking assigned to Helper 1
        ResponseEntity<Map> bookingBeforeRes = restTemplate.getForEntity("/api/bookings/" + bookingId, Map.class);
        assertEquals(HttpStatus.OK, bookingBeforeRes.getStatusCode());
        Map bookingBefore = bookingBeforeRes.getBody();
        assertEquals(h1Id.toString(), bookingBefore.get("assignedHelperId"));
        assertEquals("CONFIRMED", bookingBefore.get("status"));
        assertEquals(250.0, ((Number) bookingBefore.get("totalAmount")).doubleValue());

        // 5. Helper 1 declares Emergency Leave across the date
        HelperEmergencyCancelRequest emergencyReq = new HelperEmergencyCancelRequest(bookingDate, bookingDate, "Severe health crisis");
        ResponseEntity<EmergencyCancelResponse> emergencyRes = restTemplate.postForEntity(
                "/api/helpers/" + h1Id + "/emergency-cancel", emergencyReq, EmergencyCancelResponse.class);

        assertEquals(HttpStatus.ACCEPTED, emergencyRes.getStatusCode());
        assertNotNull(emergencyRes.getBody());
        assertEquals(1, emergencyRes.getBody().getTotalBookingsCancelled());
        assertEquals(1, emergencyRes.getBody().getTasksEnqueued());

        // 6. Verify booking entered PENDING_REASSIGNMENT
        ResponseEntity<Map> bookingPendingRes = restTemplate.getForEntity("/api/bookings/" + bookingId, Map.class);
        assertEquals(HttpStatus.OK, bookingPendingRes.getStatusCode());
        Map bookingPending = bookingPendingRes.getBody();
        assertEquals("PENDING_REASSIGNMENT", bookingPending.get("status"));

        // 7. Manually trigger reassignment worker to process pending queue
        ResponseEntity<List> processRes = restTemplate.postForEntity("/api/reassignments/process", null, List.class);
        assertEquals(HttpStatus.OK, processRes.getStatusCode());

        // 8. Verify booking is now REASSIGNED to Helper 2, restored to CONFIRMED, and original price retained!
        ResponseEntity<Map> bookingAfterRes = restTemplate.getForEntity("/api/bookings/" + bookingId, Map.class);
        assertEquals(HttpStatus.OK, bookingAfterRes.getStatusCode());
        Map bookingAfter = bookingAfterRes.getBody();
        assertEquals("CONFIRMED", bookingAfter.get("status"));
        assertEquals(h2Id.toString(), bookingAfter.get("assignedHelperId"));
        assertEquals(250.0, ((Number) bookingAfter.get("totalAmount")).doubleValue()); // Protected price preserved!
    }

    @Test
    @DisplayName("End-to-End: Helper Emergency Cancellation falls back gracefully to 100% refund when no substitute exists")
    void helperEmergencyCancellationNoSubstituteFallbackRefund() {
        LocalDate bookingDate = LocalDate.now().plusDays(4);
        LocalTime startTime = LocalTime.of(16, 0);
        LocalTime endTime = LocalTime.of(17, 0);

        // 1. Onboard ONLY 1 Helper in this locality
        HelperOnboardRequest hReq = HelperOnboardRequest.builder()
                .name("Anita Pal")
                .phone("9876599999")
                .gender(Gender.FEMALE)
                .localities(Set.of("Yelahanka"))
                .skills(Set.of(SkillType.ELDER_CARE))
                .hourlyRate(400.0)
                .governmentIdProof("PROOF-3")
                .build();
        UUID helperId = restTemplate.postForEntity("/api/helpers", hReq, HelperSearchResponse.class).getBody().getId();

        // Add availability
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<AvailabilityRequest> slotReq = List.of(AvailabilityRequest.builder()
                .slotDate(bookingDate).startTime(startTime).endTime(endTime).status(AvailabilityStatus.AVAILABLE).build());
        restTemplate.exchange("/api/helpers/" + helperId + "/availability", HttpMethod.PUT, new HttpEntity<>(slotReq, headers), Map.class);

        // 2. Customer books Helper
        UUID customerId = restTemplate.postForEntity("/api/customers",
                new CustomerRequest("Deepak Roy", "Yelahanka New Town"), CustomerResponse.class).getBody().getId();

        UnifiedBookingRequest bookingReq = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(customerId)
                .locality("Yelahanka")
                .skill(SkillType.ELDER_CARE)
                .bookingDate(bookingDate)
                .startTime(startTime)
                .endTime(endTime)
                .paymentMethod(PaymentMethod.WALLET)
                .build();

        BookingResult bookingResult = restTemplate.postForEntity("/api/bookings", bookingReq, BookingResult.class).getBody();
        UUID bookingId = bookingResult.getId();
        UUID paymentId = bookingResult.getBookings().get(0).getPaymentId();

        // Confirm payment
        restTemplate.exchange("/api/payments/" + paymentId + "/status",
                HttpMethod.PATCH, new HttpEntity<>(new PaymentStatusUpdateRequest(PaymentStatus.SUCCESS), headers), Map.class);

        // 3. Helper triggers emergency cancellation for this booking
        HelperEmergencyCancelRequest emergencyReq = new HelperEmergencyCancelRequest(bookingDate, bookingDate, "Family emergency");
        restTemplate.postForEntity("/api/helpers/" + helperId + "/emergency-cancel", emergencyReq, EmergencyCancelResponse.class);

        // 4. Run queue worker
        restTemplate.postForEntity("/api/reassignments/process", null, List.class);

        // 5. Verify booking was auto-cancelled
        ResponseEntity<Map> bookingCancelledRes = restTemplate.getForEntity("/api/bookings/" + bookingId, Map.class);
        assertEquals(HttpStatus.OK, bookingCancelledRes.getStatusCode());
        Map bookingCancelled = bookingCancelledRes.getBody();
        assertEquals("CANCELLED", bookingCancelled.get("status"));

        // 6. Verify full refund record created in payment history in DB
        List<Payment> payments = paymentRepository.findByBookingId(bookingId);
        assertTrue(payments.stream().anyMatch(p ->
                p.getPaymentType() == PaymentType.CANCEL_REFUND &&
                Double.valueOf(400.0).equals(p.getAmount())
        ));
    }

    @Test
    @DisplayName("Validation: Rejects fractional minute bookings (e.g. 15 or 30 mins) with HTTP 400 Bad Request")
    void rejectFractionalDurationBookings() {
        UUID customerId = restTemplate.postForEntity("/api/customers",
                new CustomerRequest("Maya Sen", "MG Road"), CustomerResponse.class).getBody().getId();

        UnifiedBookingRequest req = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(customerId)
                .locality("MG Road")
                .skill(SkillType.CLEANING)
                .bookingDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 15)) // Fractional!
                .endTime(LocalTime.of(11, 15))
                .paymentMethod(PaymentMethod.CARD)
                .build();

        ResponseEntity<Map> res = restTemplate.postForEntity("/api/bookings", req, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
    }

    @Test
    @DisplayName("Validation: Customer review submission is rejected if service is not yet completed")
    void rejectReviewBeforeServiceCompletion() {
        LocalDate bookingDate = LocalDate.now().plusDays(2);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        HelperOnboardRequest hReq = HelperOnboardRequest.builder()
                .name("Lata Devi")
                .phone("9876543111")
                .gender(Gender.FEMALE)
                .localities(Set.of("Jayanagar"))
                .skills(Set.of(SkillType.CLEANING))
                .hourlyRate(200.0)
                .governmentIdProof("PROOF-4")
                .build();
        UUID helperId = restTemplate.postForEntity("/api/helpers", hReq, HelperSearchResponse.class).getBody().getId();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<AvailabilityRequest> slotReq = List.of(AvailabilityRequest.builder()
                .slotDate(bookingDate).startTime(startTime).endTime(endTime).status(AvailabilityStatus.AVAILABLE).build());
        restTemplate.exchange("/api/helpers/" + helperId + "/availability", HttpMethod.PUT, new HttpEntity<>(slotReq, headers), Map.class);

        UUID customerId = restTemplate.postForEntity("/api/customers",
                new CustomerRequest("Tanvi Sen", "Jayanagar 4th Block"), CustomerResponse.class).getBody().getId();

        UnifiedBookingRequest bookingReq = UnifiedBookingRequest.builder()
                .bookingType(BookingType.SCHEDULED)
                .customerId(customerId)
                .locality("Jayanagar")
                .skill(SkillType.CLEANING)
                .bookingDate(bookingDate)
                .startTime(startTime)
                .endTime(endTime)
                .paymentMethod(PaymentMethod.UPI)
                .build();

        BookingResult bookingResult = restTemplate.postForEntity("/api/bookings", bookingReq, BookingResult.class).getBody();
        UUID bookingId = bookingResult.getId();

        // Attempt to review while booking is PENDING_PAYMENT / CONFIRMED
        CustomerReviewRequest reviewReq = CustomerReviewRequest.builder()
                .bookingId(bookingId)
                .helperId(helperId)
                .rating(5)
                .review("Attempting early review")
                .build();

        ResponseEntity<Map> reviewRes = restTemplate.postForEntity(
                "/api/customers/" + customerId + "/reviews", reviewReq, Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, reviewRes.getStatusCode());
    }
}
