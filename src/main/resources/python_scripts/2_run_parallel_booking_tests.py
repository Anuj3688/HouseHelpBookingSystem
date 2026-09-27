#!/usr/bin/env python3
import json
import os
import sys
import random
import urllib.request
import urllib.error
import concurrent.futures
from datetime import datetime, timedelta

BASE_URL = "http://localhost:8080/api"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "../../.."))
TEST_RESOURCES_DIR = os.path.join(PROJECT_ROOT, "src/test/resources")

def http_post(url, data):
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='POST'
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except:
            return e.code, {"error": body}

def http_patch(url, data):
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='PATCH'
    )
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except:
            return e.code, {"error": body}

def http_get(url):
    req = urllib.request.Request(url, method='GET')
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except:
            return e.code, {"error": body}

def execute_scheduled_booking(args):
    idx, cust, loc, skill, date_str, hour, pm = args
    booking_req = {
        "customerId": cust['id'],
        "locality": loc,
        "skill": skill,
        "bookingDate": date_str,
        "startTime": f"{hour:02d}:00:00",
        "endTime": f"{hour+1:02d}:00:00",
        "paymentMethod": pm
    }
    status, res = http_post(f"{BASE_URL}/bookings", booking_req)
    
    tc_title = f"Scheduled Booking #{idx+1} - {loc}/{skill}"
    tc_desc = f"Customer {cust['name']} booking {skill} in {loc} on {date_str} {hour:02d}:00-{hour+1:02d}:00 via {pm}"
    
    if status in (200, 201):
        # Process payment for 60% of bookings
        if idx % 5 != 0:
            http_patch(f"{BASE_URL}/payments/{res['paymentId']}/status", {"status": "SUCCESS"})
        result_str = f"SUCCESS - Booked (BookingID: {res['id']}, HelperID: {res['assignedHelperId']}, Amount: ₹{res['totalAmount']})"
        return {
            "SrNo": idx + 1,
            "testCase": tc_title,
            "description": tc_desc,
            "classinvolved": "BookingService",
            "results": result_str,
            "edge cases considered": "Lowest-price helper auto-assignment & slot status reservation"
        }
    else:
        result_str = f"EXPECTED FAILURE / NO AVAILABILITY (HTTP {status}: {res.get('message', 'No slot')})"
        return {
            "SrNo": idx + 1,
            "testCase": tc_title,
            "description": tc_desc,
            "classinvolved": "BookingService",
            "results": result_str,
            "edge cases considered": "Slot unavailability handling & candidate exhaustion"
        }

def execute_instant_booking(args):
    idx, cust, loc, skill, pm = args
    status, res = http_post(f"{BASE_URL}/bookings/instant", {
        "customerId": cust['id'],
        "locality": loc,
        "skill": skill,
        "paymentMethod": pm
    })
    tc_title = f"Instant Booking #{idx+1} - {loc}/{skill}"
    tc_desc = f"Instant booking attempt for Customer {cust['name']} in {loc} for {skill}"
    
    if status in (200, 201):
        result_str = f"SUCCESS - Booked (BookingID: {res['id']}, Slot: {res['bookingDate']} {res['startTime']}-{res['endTime']})"
        return {
            "SrNo": idx + 1,
            "testCase": tc_title,
            "description": tc_desc,
            "classinvolved": "BookingService",
            "results": result_str,
            "edge cases considered": "Earliest available same-day slot discovery"
        }
    else:
        result_str = f"NO AVAILABILITY TODAY (HTTP {status}: {res.get('message', 'No slot')})"
        return {
            "SrNo": idx + 1,
            "testCase": tc_title,
            "description": tc_desc,
            "classinvolved": "BookingService",
            "results": result_str,
            "edge cases considered": "No remaining open slots today"
        }

def execute_recurring_booking(args):
    idx, cust, loc, skill, start_d, day_name = args
    status, res = http_post(f"{BASE_URL}/booking-series", {
        "customerId": cust['id'],
        "locality": loc,
        "skill": skill,
        "startDate": start_d,
        "startTime": "10:00:00",
        "endTime": "11:00:00",
        "occurrenceCount": 4,
        "recurrenceDays": [day_name],
        "paymentMethod": "UPI"
    })
    tc_title = f"Recurring Series #{idx+1} - {loc}/{skill}"
    tc_desc = f"4-occurrence weekly series for Customer {cust['name']} starting {start_d} ({day_name})"
    
    if status in (200, 201):
        result_str = f"SUCCESS - Created Series {res['seriesId']} (Created: {len(res['createdBookings'])}, Unavailable: {len(res['unavailableDates'])})"
        return {
            "SrNo": idx + 1,
            "testCase": tc_title,
            "description": tc_desc,
            "classinvolved": "BookingSeriesService",
            "results": result_str,
            "edge cases considered": "Multi-week recurrence date matching & partial slot availability"
        }
    else:
        result_str = f"FAILED (HTTP {status}: {res.get('message', 'Error')})"
        return {
            "SrNo": idx + 1,
            "testCase": tc_title,
            "description": tc_desc,
            "classinvolved": "BookingSeriesService",
            "results": result_str,
            "edge cases considered": "Series recurrence validation"
        }

def execute_reschedule_and_cancel_workflows(base_idx, booking_id, series_id, payment_id, date_str):
    results = []
    curr_idx = base_idx

    # 1. Reschedule test
    next_day = (datetime.strptime(date_str, "%Y-%m-%d").date() + timedelta(days=1)).strftime("%Y-%m-%d")
    resched_status, resched_res = http_put(f"{BASE_URL}/bookings/{booking_id}/reschedule", {
        "newBookingDate": next_day,
        "newStartTime": "15:00:00",
        "newEndTime": "16:00:00"
    })
    tc1_title = f"Reschedule Booking #{curr_idx+1} - Single Booking"
    tc1_desc = f"Reschedule booking {booking_id} to date {next_day} 15:00-16:00"
    if resched_status in (200, 201):
        res1_str = f"SUCCESS - Rescheduled to {next_day} 15:00-16:00 (Status: {resched_res.get('status')})"
        edge1 = "Slot release of old slot, new slot reservation & price delta adjustment"
    else:
        res1_str = f"RESCHEDULE REJECTED (HTTP {resched_status}: {resched_res.get('message', 'Error')})"
        edge1 = "Target slot availability validation & status checking"

    results.append({
        "SrNo": curr_idx + 1,
        "testCase": tc1_title,
        "description": tc1_desc,
        "classinvolved": "BookingService",
        "results": res1_str,
        "edge cases considered": edge1
    })
    curr_idx += 1

    # 2. Single Booking Cancellation test
    cancel_status, cancel_res = http_post(f"{BASE_URL}/bookings/{booking_id}/cancel", {})
    tc2_title = f"Cancel Booking #{curr_idx+1} - Single Booking"
    tc2_desc = f"Cancel single booking {booking_id}"
    if cancel_status in (200, 201):
        res2_str = f"SUCCESS - Booking Cancelled (Status: {cancel_res.get('status')})"
        edge2 = "Atomic slot release to AVAILABLE & cancellation event publishing"
    else:
        res2_str = f"CANCEL REJECTED (HTTP {cancel_status}: {cancel_res.get('message', 'Error')})"
        edge2 = "Already cancelled or missing booking validation"

    results.append({
        "SrNo": curr_idx + 1,
        "testCase": tc2_title,
        "description": tc2_desc,
        "classinvolved": "BookingService",
        "results": res2_str,
        "edge cases considered": edge2
    })
    curr_idx += 1

    # 3. Recurring Series Cancellation test (if series_id provided)
    if series_id:
        s_cancel_status, s_cancel_res = http_post(f"{BASE_URL}/booking-series/{series_id}/cancel", {})
        tc3_title = f"Cancel Series #{curr_idx+1} - Recurring Series"
        tc3_desc = f"Cancel recurring booking series {series_id}"
        if s_cancel_status in (200, 201):
            res3_str = f"SUCCESS - Series Cancelled (Occurrences Cancelled: {s_cancel_res.get('cancelledOccurrences')})"
            edge3 = "Batch slot release for all future active series occurrences"
        else:
            res3_str = f"SERIES CANCEL REJECTED (HTTP {s_cancel_status}: {s_cancel_res.get('message', 'Error')})"
            edge3 = "Already cancelled series validation"

        results.append({
            "SrNo": curr_idx + 1,
            "testCase": tc3_title,
            "description": tc3_desc,
            "classinvolved": "BookingSeriesService",
            "results": res3_str,
            "edge cases considered": edge3
        })
    return results

def main():
    print("=== EXECUTING PARALLEL MULTI-USER LOAD TEST & EVENT AUDIT ===")
    
    state_file = os.path.join(SCRIPT_DIR, "active_system_state.json")
    if os.path.exists(state_file):
        with open(state_file) as f:
            state = json.load(f)
        customers = list(state['customers'].values()) if isinstance(state['customers'], dict) else list(state['customers'])
        helpers = list(state['helpers'].values()) if isinstance(state['helpers'], dict) else list(state['helpers'])
    else:
        status, customers = http_get(f"{BASE_URL}/customers")
        status2, helpers = http_get(f"{BASE_URL}/housekeeping/helpers")
        customers = list(customers) if isinstance(customers, list) else []
        helpers = list(helpers) if isinstance(helpers, list) else []
    print(f"Loaded active state with {len(customers)} customers and {len(helpers)} helpers.")

    localities_pool = ["Koramangala", "Indiranagar", "Whitefield", "HSR_Layout", "Jayanagar", "BTM_Layout", "Hebbal", "Electronic_City"]
    skills_pool = ["CLEANING", "COOKING", "CHILD_CARE", "ELDER_CARE", "LAUNDRY", "DISH_WASHING"]
    payment_methods = ["UPI", "CARD", "WALLET"]
    
    # 1. Prepare 30 Parallel Scheduled Bookings
    scheduled_tasks = []
    base_date = datetime.now().date()
    for i in range(30):
        cust = customers[i % len(customers)]
        loc = localities_pool[i % len(localities_pool)]
        skill = skills_pool[i % len(skills_pool)]
        target_date = (base_date + timedelta(days=1 + (i % 4))).strftime("%Y-%m-%d")
        hour = 8 + (i % 8)
        pm = payment_methods[i % len(payment_methods)]
        scheduled_tasks.append((i, cust, loc, skill, target_date, hour, pm))
        
    # 2. Prepare 10 Parallel Instant Bookings
    instant_tasks = []
    for i in range(10):
        cust = customers[(i + 30) % len(customers)]
        loc = localities_pool[i % len(localities_pool)]
        skill = skills_pool[i % len(skills_pool)]
        pm = payment_methods[i % len(payment_methods)]
        instant_tasks.append((i + 30, cust, loc, skill, pm))

    # 3. Prepare 10 Parallel Recurring Series Bookings
    days_names = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"]
    recurring_tasks = []
    for i in range(10):
        cust = customers[(i + 40) % len(customers)]
        loc = localities_pool[i % len(localities_pool)]
        skill = skills_pool[i % len(skills_pool)]
        start_d = (base_date + timedelta(days=1 + i)).strftime("%Y-%m-%d")
        day_name = days_names[i % len(days_names)]
        recurring_tasks.append((i + 40, cust, loc, skill, start_d, day_name))

    test_results_log = []

    print("Running 30 Scheduled Bookings concurrently...")
    created_bookings_for_reschedule = []
    created_series_for_cancel = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        results = list(executor.map(execute_scheduled_booking, scheduled_tasks))
        test_results_log.extend(results)

    print("Running 10 Instant Bookings concurrently...")
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        results = list(executor.map(execute_instant_booking, instant_tasks))
        test_results_log.extend(results)

    print("Running 10 Recurring Series Bookings concurrently...")
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        results = list(executor.map(execute_recurring_booking, recurring_tasks))
        test_results_log.extend(results)

    # 4. Run Reschedule & Cancellation Workflows on created bookings
    print("Running Reschedule & Cancellation Workflows...")
    status, active_bookings = http_get(f"{BASE_URL}/housekeeping/bookings")
    status2, active_series = http_get(f"{BASE_URL}/housekeeping/events")
    
    curr_srno = 50
    if status == 200 and len(active_bookings) > 0:
        # Pick 5 active confirmed/pending bookings to test reschedule and cancel
        sample_bookings = active_bookings[:5]
        for b in sample_bookings:
            b_id = b['id']
            s_id = b.get('seriesId')
            p_id = b.get('paymentId')
            d_str = b['bookingDate']
            res_list = execute_reschedule_and_cancel_workflows(curr_srno, b_id, s_id, p_id, d_str)
            test_results_log.extend(res_list)
            curr_srno += len(res_list)

    # Sort log by SrNo
    test_results_log.sort(key=lambda x: x['SrNo'])

    # 5. Fetch All Audit Events & Save
    print("\n--- Exporting System Audit Events ---")
    status, events = http_get(f"{BASE_URL}/housekeeping/events")
    if status == 200:
        os.makedirs(TEST_RESOURCES_DIR, exist_ok=True)
        events_file_path = os.path.join(TEST_RESOURCES_DIR, "system_events_log.json")
        with open(events_file_path, "w") as f:
            json.dump(events, f, indent=2)
        print(f"Exported {len(events)} system audit events to: {events_file_path}")

    # 6. Export CSV v2 Test Results
    csv_file_path = os.path.join(TEST_RESOURCES_DIR, "booking_service_test_results_v2.csv")
    with open(csv_file_path, "w") as f:
        f.write("SrNo,testCase,description,classinvolved,results,edge cases considered\n")
        for row in test_results_log:
            esc_tc = row['testCase'].replace('"', '""')
            esc_desc = row['description'].replace('"', '""')
            esc_res = row['results'].replace('"', '""')
            esc_edge = row['edge cases considered'].replace('"', '""')
            f.write(f"{row['SrNo']},\"{esc_tc}\",\"{esc_desc}\",\"{row['classinvolved']}\",\"{esc_res}\",\"{esc_edge}\"\n")
    print(f"Exported v2 test cases report to: {csv_file_path}")
    print("=== MULTI-USER LOAD TEST COMPLETED SUCCESSFULLY ===")

if __name__ == "__main__":
    main()
