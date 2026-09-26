package com.househelper.resources;

import com.househelper.dto.BookingSeriesCancellationResponse;
import com.househelper.dto.BookingSeriesRequest;
import com.househelper.dto.BookingSeriesResponse;
import com.househelper.service.BookingSeriesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/booking-series")
@RequiredArgsConstructor
public class BookingSeriesResource {

    private final BookingSeriesService bookingSeriesService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingSeriesResponse createWeeklySeries(@Valid @RequestBody BookingSeriesRequest request) {
        return bookingSeriesService.createWeeklySeries(request);
    }

    @PostMapping("/{seriesId}/cancel")
    public BookingSeriesCancellationResponse cancelSeries(@PathVariable Long seriesId) {
        return bookingSeriesService.cancelSeries(seriesId);
    }
}
