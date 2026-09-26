maid-booking-system/
├── pom.xml
└── src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── example/
│   │           └── maidbooking/
│   │               ├── MaidBookingApplication.java
│   │               │
│   │               ├── controller/
│   │               │   ├── MaidController.java
│   │               │   └── BookingController.java
│   │               │
│   │               ├── service/
│   │               │   ├── MaidService.java
│   │               │   └── BookingService.java
│   │               │
│   │               ├── repository/
│   │               │   ├── MaidRepository.java
│   │               │   ├── MaidAvailabilityRepository.java
│   │               │   ├── BookingRepository.java
│   │               │   └── PaymentRepository.java
│   │               │
│   │               ├── model/
│   │               │   ├── Maid.java
│   │               │   ├── MaidAvailability.java
│   │               │   ├── Booking.java
│   │               │   └── Payment.java
│   │               │
│   │               ├── dto/
│   │               │   ├── MaidOnboardRequest.java
│   │               │   ├── AvailabilityUpdateRequest.java
│   │               │   ├── MaidSearchResponse.java
│   │               │   ├── BookingRequest.java
│   │               │   └── RescheduleRequest.java
│   │               │
│   │               ├── converter/
│   │               │   └── EncryptedStringConverter.java
│   │               │
│   │               └── exception/
│   │                   ├── GlobalExceptionHandler.java
│   │                   └── SlotUnavailableException.java
│   │
│   └── resources/
│       └── application.yml  (or application.properties)
│
└── test/
└── java/
└── com/
└── example/
└── maidbooking/
└── MaidBookingApplicationTests.java