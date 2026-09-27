Design and implement the backend for a maid (home-services) booking platform. Customers discover maids and
book them — instantly, on a schedule, or on a recurring basis; maids are onboarded onto the platform with their
skills and availability. A booking is paid for and can later be cancelled.
Functional requirements:
3.1 Maid Discovery
• Search and browse maids by criteria such as locality, service type (for example cleaning, cooking,
dishwashing), and required date or time.
• Support filters such as rating, price, skills, and gender preference — and design so that new filters can be
added later without reworking the search.
• Return only maids who are actually available for the requested time.
3.2 Addition of Maid
• Onboard a new maid with profile details: name, location, skills or services offered, pricing, and availability
windows.
• Model a maid's availability so the system can reason about free versus busy slots.
3.3 Booking a Maid
• Support three booking types behind a common abstraction — Instant (book a maid available right now),
Scheduled (book for a specific future date and time), and Recurring (a repeating slot, for example every weekday
at 9am or every Sunday).
• Check availability and prevent double-booking of the same maid for overlapping slots.
• Ensure adding a new booking type later requires minimal change.
3.4 Payment
• Take payment for a booking.
• Support multiple payment methods (for example card, UPI, wallet) behind a common abstraction, so new
methods can be plugged in.
• For recurring bookings, consider how payment is handled per occurrence.
3.5 Cancellation
• Cancel an existing booking — for recurring bookings, support cancelling a single occurrence or the whole
series.
• Apply a cancellation and refund policy — simple, but pluggable.
• Free up the maid's slot so it becomes discoverable and bookable again.