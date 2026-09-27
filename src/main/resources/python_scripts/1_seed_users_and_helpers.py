#!/usr/bin/env python3
import json
import os
import sys
import urllib.request
import urllib.error
import concurrent.futures
from datetime import datetime, timedelta

BASE_URL = "http://localhost:8080/api"
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

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

def http_put(url, data):
    req = urllib.request.Request(
        url,
        data=json.dumps(data).encode('utf-8'),
        headers={'Content-Type': 'application/json'},
        method='PUT'
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

def register_customer(cust):
    status, res = http_post(f"{BASE_URL}/customers", cust)
    if status in (200, 201):
        return res
    return None

def register_helper_and_slots(helper):
    status, res = http_post(f"{BASE_URL}/helpers", helper)
    if status in (200, 201):
        helper_id = res['id']
        availability_requests = []
        base_date = datetime.now().date()
        for day_offset in range(0, 7):
            slot_date = (base_date + timedelta(days=day_offset)).strftime("%Y-%m-%d")
            for hour in [8, 9, 10, 11, 14, 15, 16, 17]:
                availability_requests.append({
                    "slotDate": slot_date,
                    "startTime": f"{hour:02d}:00:00",
                    "endTime": f"{hour+1:02d}:00:00",
                    "status": "AVAILABLE"
                })
        http_put(f"{BASE_URL}/helpers/{helper_id}/availability", availability_requests)
        return res
    return None

def main():
    print("=== SEEDING USERS & HELPERS (PARALLEL EXECUTION) ===")
    
    customers_file = os.path.join(SCRIPT_DIR, "customers_seed.json")
    helpers_file = os.path.join(SCRIPT_DIR, "helpers_seed.json")
    
    if not os.path.exists(customers_file) or not os.path.exists(helpers_file):
        print("Seed files not found. Run generate_customers.py and generate_helpers.py first.")
        sys.exit(1)
        
    with open(customers_file) as f:
        customers_data = json.load(f)
    with open(helpers_file) as f:
        helpers_data = json.load(f)
        
    print(f"Loaded {len(customers_data)} customer records and {len(helpers_data)} helper records.")

    # 1. Register Customers in parallel
    created_customers = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        results = list(executor.map(register_customer, customers_data))
        created_customers = [r for r in results if r is not None]
    print(f"Successfully registered {len(created_customers)} customers.")

    # 2. Register Helpers + Slots in parallel
    created_helpers = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=15) as executor:
        results = list(executor.map(register_helper_and_slots, helpers_data))
        created_helpers = [r for r in results if r is not None]
    print(f"Successfully registered {len(created_helpers)} helpers with 7 days of availability slots.")

    # Save registered active IDs for the testing script
    state = {
        "customers": created_customers,
        "helpers": created_helpers,
        "seededAt": datetime.now().isoformat()
    }
    state_file = os.path.join(SCRIPT_DIR, "active_system_state.json")
    with open(state_file, "w") as f:
        json.dump(state, f, indent=2)
    print(f"Active state saved to: {state_file}")

if __name__ == "__main__":
    main()
