# HoseHelpBookingSystem

Spring Boot API for searching, booking, and managing house-help services.

## Assumptions

- Helper availability is provided as fixed one-hour slots that start on the hour, such as `09:00`-`10:00` and `10:00`-`11:00`. Multi-hour ranges and fractional-hour slots such as `10:30`-`11:30` are not accepted.
- A helper cannot have overlapping availability. The fixed one-hour slot rule and unique helper/date/start-time key prevent overlaps for newly submitted slots.

See [api_contracts_summary.md](api_contracts_summary.md) for API routes and request examples.
